package com.sannyas.player.playback

import androidx.media3.common.Player
import com.sannyas.player.domain.model.SleepTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Sleep timer.
 *
 * Lives outside the service so the UI can observe and configure it before the
 * service exists; [attach] hands it the player once playback starts.
 *
 * The timer does not cut playback dead. Most of this catalogue is listened to
 * in bed, and a discourse stopping mid-sentence at full volume is exactly the
 * thing that wakes someone who was nearly asleep — so the last stretch is a
 * volume ramp, and the audio is already inaudible by the time the player
 * actually pauses.
 */
@Singleton
class SleepTimerController @Inject constructor() {

    private val _state = MutableStateFlow<SleepTimer>(SleepTimer.Off)
    val state: StateFlow<SleepTimer> = _state.asStateFlow()

    private var player: Player? = null
    private var scope: CoroutineScope? = null
    private var countdown: Job? = null

    /**
     * The level to put back once the ramp is over.
     *
     * Captured at the start of a fade rather than assumed to be 1f: whatever
     * the player was at is the user's choice, and restoring it is what stops
     * the next talk from starting silently.
     */
    private var volumeBeforeFade: Float? = null

    fun attach(player: Player, scope: CoroutineScope) {
        this.player = player
        this.scope = scope
        // Re-arm if the user set a timer before playback started.
        (_state.value as? SleepTimer.Duration)?.let { start(it.totalMs) }
    }

    fun detach() {
        countdown?.cancel()
        countdown = null
        restoreVolume()
        player = null
        scope = null
    }

    /** Fades out and pauses after [millis]. */
    fun start(millis: Long) {
        countdown?.cancel()
        restoreVolume()

        if (millis <= 0L) {
            _state.value = SleepTimer.Off
            return
        }

        // A fixed 45 s ramp on a 5-minute timer would spend a sixth of the
        // session fading, so short timers get a proportionally shorter one.
        val fadeMs = min(FADE_MS, millis / 4)
        _state.value = SleepTimer.Duration(totalMs = millis, remainingMs = millis, fadeMs = fadeMs)

        val activeScope = scope ?: return
        countdown = activeScope.launch {
            var remaining = millis
            while (isActive && remaining > 0L) {
                // Ticking every second is plenty for the readout but audibly
                // steps the ramp, so the fade window runs at a finer grain.
                val step = if (remaining <= fadeMs) FADE_TICK_MS else TICK_MS
                delay(step)
                remaining = (remaining - step).coerceAtLeast(0L)

                val current = _state.value as? SleepTimer.Duration ?: return@launch
                // Only re-publish when the displayed second changes; the fade
                // ticks ten times faster than anything on screen needs.
                if (remaining / 1000L != current.remainingMs / 1000L || remaining == 0L) {
                    _state.value = current.copy(remainingMs = remaining)
                }
                if (remaining <= fadeMs) applyFade(remaining, fadeMs)
            }
            finish()
        }
    }

    /** Stops when the talk currently playing ends. */
    fun startEndOfTrack() {
        countdown?.cancel()
        countdown = null
        restoreVolume()
        _state.value = SleepTimer.EndOfTrack
    }

    fun cancel() {
        countdown?.cancel()
        countdown = null
        restoreVolume()
        _state.value = SleepTimer.Off
    }

    /**
     * Called by [PlayerConnection] when a track transition happens, so the
     * end-of-track mode can pause exactly once.
     *
     * No ramp here — the recording has already run out, which is the gentlest
     * ending available.
     */
    fun onTrackEnded() {
        if (_state.value is SleepTimer.EndOfTrack) finish()
    }

    /**
     * Ramps the volume down across the closing [fadeMs].
     *
     * Squared rather than linear: perceived loudness rises far more slowly than
     * amplitude, so a linear ramp stays plainly audible almost to the end and
     * then vanishes — which is the abrupt stop this exists to avoid.
     */
    private fun applyFade(remainingMs: Long, fadeMs: Long) {
        val target = player ?: return
        if (volumeBeforeFade == null) volumeBeforeFade = target.volume
        val base = volumeBeforeFade ?: return
        val fraction = (remainingMs.toFloat() / fadeMs).coerceIn(0f, 1f)
        target.volume = base * fraction * fraction
    }

    private fun finish() {
        player?.pause()
        // Restored after pausing, so the level is back for the next talk
        // without a moment of full-volume audio on the way out.
        restoreVolume()
        _state.value = SleepTimer.Off
        countdown = null
    }

    private fun restoreVolume() {
        val base = volumeBeforeFade ?: return
        player?.volume = base
        volumeBeforeFade = null
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val FADE_TICK_MS = 100L

        /** Long enough to be felt as a fade rather than noticed as a dip. */
        const val FADE_MS = 45_000L
    }
}
