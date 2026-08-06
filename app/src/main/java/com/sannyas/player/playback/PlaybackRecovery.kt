package com.sannyas.player.playback

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.common.Player
import com.sannyas.player.core.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Keeps a talk playing across a network blip.
 *
 * Audio is streamed from a third-party host over whatever connection the phone
 * has at 2am, so a dropped read mid-talk is ordinary rather than exceptional.
 * Left alone, ExoPlayer surfaces one error and stops for good — the listener
 * comes back to silence and, reasonably, concludes the app is broken. This
 * re-prepares from the same position on a widening backoff instead.
 *
 * Deliberately narrow about what it retries. A 404 or an unsupported file will
 * fail identically forever, and hammering it only delays telling the truth; only
 * errors that a working connection would have avoided are worth another go.
 *
 * Lives beside the player in the service rather than in the UI layer, because
 * the case that matters most is the one where there is no UI: screen off, phone
 * face down, ninety minutes to go.
 */
@Singleton
class PlaybackRecovery @Inject constructor(
    private val networkMonitor: NetworkMonitor,
) {

    /**
     * Which side of the connection is at fault.
     *
     * The player cannot tell these apart on its own — a timeout looks the same
     * whether the phone has no signal or the host has stopped answering — so it
     * is settled once, at the moment of failure, by asking the device.
     */
    enum class Outage { NONE, OFFLINE, SOURCE_UNREACHABLE }

    /** What the UI needs to say something honest while this is happening. */
    data class State(
        val isReconnecting: Boolean = false,
        val attempt: Int = 0,
        /** Set once the retries are spent, so the failure is finally surfaced. */
        val gaveUp: Boolean = false,
        val outage: Outage = Outage.NONE,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var player: Player? = null
    private var scope: CoroutineScope? = null
    private var pending: Job? = null
    private var attempt = 0

    fun attach(player: Player, scope: CoroutineScope) {
        this.player = player
        this.scope = scope
    }

    fun detach() {
        pending?.cancel()
        pending = null
        player = null
        scope = null
        reset()
    }

    /**
     * Whether this is the sort of failure this class deals with.
     *
     * Pure, and safe to ask from anywhere: the UI uses it to decide whether to
     * stay quiet and let a retry happen, without having to win a race against
     * the service's own listener.
     */
    fun handles(error: PlaybackException): Boolean = error.isTransient()

    fun onPlayerError(error: PlaybackException) {
        val target = player ?: return

        // Only recover what the listener asked to hear. If they had already
        // paused, waking the stream back up on their behalf would be a
        // surprise, not a fix.
        if (!target.playWhenReady) return
        if (!error.isTransient()) return

        val outage = if (networkMonitor.isOnline()) {
            Outage.SOURCE_UNREACHABLE
        } else {
            Outage.OFFLINE
        }

        if (attempt >= MAX_ATTEMPTS) {
            _state.value = State(
                isReconnecting = false,
                attempt = attempt,
                gaveUp = true,
                outage = outage,
            )
            return
        }

        val activeScope = scope ?: return
        attempt += 1
        _state.value = State(isReconnecting = true, attempt = attempt, outage = outage)

        pending?.cancel()
        pending = activeScope.launch {
            delay(backoffFor(attempt))
            // `prepare` resumes from the current position, so the listener
            // rejoins the sentence they lost rather than the top of the talk.
            player?.prepare()
        }
    }

    /** Called when playback is genuinely running again. */
    fun onPlaybackHealthy() {
        if (attempt == 0 && !_state.value.isReconnecting) return
        pending?.cancel()
        pending = null
        reset()
    }

    /** A new talk starts with a clean slate. */
    fun onNewItem() {
        pending?.cancel()
        pending = null
        reset()
    }

    private fun reset() {
        attempt = 0
        _state.value = State()
    }

    /**
     * Doubling, capped — a phone in a tunnel is not helped by being asked
     * sixty times, and a phone whose Wi-Fi just re-associated is back within
     * the first couple of tries.
     */
    private fun backoffFor(attempt: Int): Long =
        min(BASE_DELAY_MS shl (attempt - 1), MAX_DELAY_MS)

    /**
     * A bad HTTP status is only worth another go when the server is the one
     * having a bad minute. Retrying a 404 for four minutes just delays telling
     * the listener the truth, so those fall through to the error path.
     */
    private fun PlaybackException.isTransient(): Boolean = when (errorCode) {
        in ALWAYS_TRANSIENT -> true
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
            val status = (cause as? InvalidResponseCodeException)?.responseCode
            status != null && (status >= 500 || status == 408 || status == 429)
        }

        else -> false
    }

    private companion object {
        const val BASE_DELAY_MS = 1_000L
        const val MAX_DELAY_MS = 30_000L

        /** Roughly four minutes of trying before admitting defeat. */
        const val MAX_ATTEMPTS = 12

        val ALWAYS_TRANSIENT = setOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )
    }
}
