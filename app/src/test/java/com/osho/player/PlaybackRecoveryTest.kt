package com.osho.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import com.osho.player.core.network.NetworkMonitor
import com.osho.player.playback.PlaybackRecovery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Retry behaviour.
 *
 * The failure this guards against is the one that produces one-star reviews:
 * a dropped read at 2am leaves the talk stopped for good, and the listener
 * wakes to silence. Equally, retrying a dead URL for four minutes would just
 * postpone telling them the truth — so both directions are pinned down here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackRecoveryTest {

    private fun playingPlayer(): Player = mockk(relaxed = true) {
        every { playWhenReady } returns true
    }

    /** The device itself has working internet unless a test says otherwise. */
    private fun recovery(online: Boolean = true) =
        PlaybackRecovery(mockk<NetworkMonitor> { every { isOnline() } returns online })

    private fun networkError() =
        PlaybackException("dropped", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)

    private fun httpError(status: Int) = PlaybackException(
        "http $status",
        InvalidResponseCodeException(
            /* responseCode = */ status,
            /* responseMessage = */ null,
            /* cause = */ null,
            /* headerFields = */ emptyMap(),
            /* dataSpec = */ mockk(relaxed = true),
            /* responseBody = */ ByteArray(0),
        ),
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    )

    @Test
    fun `re-prepares after a dropped connection`() = runTest {
        val player = playingPlayer()
        val recovery = recovery()
        recovery.attach(player, backgroundScope)

        recovery.onPlayerError(networkError())
        assertTrue(recovery.state.value.isReconnecting)
        verify(exactly = 0) { player.prepare() }

        advanceTimeBy(1_500L)
        runCurrent()
        verify(exactly = 1) { player.prepare() }
    }

    @Test
    fun `backs off further with each successive failure`() = runTest {
        val player = playingPlayer()
        val recovery = recovery()
        recovery.attach(player, backgroundScope)

        // Second attempt must wait longer than the first.
        recovery.onPlayerError(networkError())
        advanceTimeBy(1_500L); runCurrent()
        verify(exactly = 1) { player.prepare() }

        recovery.onPlayerError(networkError())
        advanceTimeBy(1_500L); runCurrent()
        verify(exactly = 1) { player.prepare() }

        advanceTimeBy(1_000L); runCurrent()
        verify(exactly = 2) { player.prepare() }
    }

    @Test
    fun `gives up eventually and says so`() = runTest {
        val player = playingPlayer()
        val recovery = recovery()
        recovery.attach(player, backgroundScope)

        repeat(20) {
            recovery.onPlayerError(networkError())
            advanceTimeBy(60_000L)
            runCurrent()
        }

        assertTrue("should surface the failure once spent", recovery.state.value.gaveUp)
        assertFalse(recovery.state.value.isReconnecting)
    }

    @Test
    fun `a healthy player clears the retry state`() = runTest {
        val player = playingPlayer()
        val recovery = recovery()
        recovery.attach(player, backgroundScope)

        recovery.onPlayerError(networkError())
        assertTrue(recovery.state.value.isReconnecting)

        recovery.onPlaybackHealthy()

        assertEquals(PlaybackRecovery.State(), recovery.state.value)
    }

    @Test
    fun `leaves a paused player alone`() = runTest {
        val player: Player = mockk(relaxed = true) { every { playWhenReady } returns false }
        val recovery = recovery()
        recovery.attach(player, backgroundScope)

        recovery.onPlayerError(networkError())
        advanceTimeBy(60_000L)
        runCurrent()

        verify(exactly = 0) { player.prepare() }
        assertFalse(recovery.state.value.isReconnecting)
    }

    @Test
    fun `retries a server error but never a missing file`() {
        val recovery = recovery()

        assertTrue("503 clears on its own", recovery.handles(httpError(503)))
        assertTrue(recovery.handles(httpError(429)))
        assertFalse("404 will fail identically forever", recovery.handles(httpError(404)))
        assertFalse(recovery.handles(httpError(403)))
    }

    @Test
    fun `does not touch a genuinely broken file`() = runTest {
        val player = playingPlayer()
        val recovery = recovery()
        recovery.attach(player, backgroundScope)

        recovery.onPlayerError(
            PlaybackException(
                "bad container",
                null,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            ),
        )
        advanceTimeBy(60_000L)
        runCurrent()

        verify(exactly = 0) { player.prepare() }
    }

    @Test
    fun `blames the source when the device itself is online`() = runTest {
        val player = playingPlayer()
        val recovery = recovery(online = true)
        recovery.attach(player, backgroundScope)

        recovery.onPlayerError(networkError())

        assertEquals(
            "a listener with full bars must not be told to check their network",
            PlaybackRecovery.Outage.SOURCE_UNREACHABLE,
            recovery.state.value.outage,
        )
    }

    @Test
    fun `blames the connection when the device is offline`() = runTest {
        val player = playingPlayer()
        val recovery = recovery(online = false)
        recovery.attach(player, backgroundScope)

        recovery.onPlayerError(networkError())

        assertEquals(PlaybackRecovery.Outage.OFFLINE, recovery.state.value.outage)
    }

    @Test
    fun `keeps the diagnosis when it finally gives up`() = runTest {
        val player = playingPlayer()
        val recovery = recovery(online = true)
        recovery.attach(player, backgroundScope)

        repeat(20) {
            recovery.onPlayerError(networkError())
            advanceTimeBy(60_000L)
            runCurrent()
        }

        assertTrue(recovery.state.value.gaveUp)
        assertEquals(
            "the give-up message has to name the same culprit the retries did",
            PlaybackRecovery.Outage.SOURCE_UNREACHABLE,
            recovery.state.value.outage,
        )
    }

    @Test
    fun `a read timeout from a silent host is retried`() {
        // Exactly what oshoworld.com does when it stalls: TCP connects, then
        // nothing comes back.
        assertTrue(
            recovery().handles(
                PlaybackException(
                    "read timed out",
                    java.net.SocketTimeoutException(),
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                ),
            ),
        )
    }
}
