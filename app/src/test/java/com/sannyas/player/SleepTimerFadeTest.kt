package com.sannyas.player

import androidx.media3.common.Player
import com.sannyas.player.domain.model.SleepTimer
import com.sannyas.player.playback.SleepTimerController
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The closing ramp.
 *
 * Worth a test rather than a listen: the interesting moment is 45 seconds into
 * a 90-minute session, and the failure mode — playback resuming silently the
 * next morning because the volume was never put back — is invisible until a
 * user hits it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerFadeTest {

    private fun fakePlayer(volumes: MutableList<Float>): Player = mockk(relaxed = true) {
        every { volume } returns USER_VOLUME
        every { volume = any() } answers { volumes += firstArg<Float>() }
    }

    @Test
    fun `leaves the volume alone until the closing ramp`() = runTest {
        val volumes = mutableListOf<Float>()
        val controller = SleepTimerController()
        controller.attach(fakePlayer(volumes), backgroundScope)

        // A one-minute timer ramps over its final quarter, so 15 s.
        controller.start(60_000L)
        advanceTimeBy(44_000L)
        runCurrent()

        assertTrue("volume touched before the ramp: $volumes", volumes.isEmpty())
    }

    @Test
    fun `ramps down to silence, then pauses and restores the level`() = runTest {
        val volumes = mutableListOf<Float>()
        val player = fakePlayer(volumes)
        val controller = SleepTimerController()
        controller.attach(player, backgroundScope)

        controller.start(60_000L)

        advanceTimeBy(52_000L)
        runCurrent()
        val midRamp = volumes.last()
        assertTrue("expected a partial duck, got $midRamp", midRamp in 0.01f..0.9f)

        advanceTimeBy(9_000L)
        runCurrent()

        verify { player.pause() }
        // The last write is the restore, so the next talk starts at the level
        // the listener chose rather than at whatever the ramp ended on.
        assertEquals(USER_VOLUME, volumes.last(), 0.0001f)
        assertEquals(SleepTimer.Off, controller.state.value)
    }

    @Test
    fun `the ramp only descends`() = runTest {
        val volumes = mutableListOf<Float>()
        val controller = SleepTimerController()
        controller.attach(fakePlayer(volumes), backgroundScope)

        controller.start(60_000L)
        advanceTimeBy(59_500L)
        runCurrent()

        assertTrue("ramp too coarse to be inaudible: ${volumes.size} steps", volumes.size > 100)
        volumes.zipWithNext { previous, next ->
            assertTrue("ramp rose from $previous to $next", next <= previous + 0.0001f)
        }
    }

    @Test
    fun `cancelling mid-ramp puts the volume back`() = runTest {
        val volumes = mutableListOf<Float>()
        val controller = SleepTimerController()
        controller.attach(fakePlayer(volumes), backgroundScope)

        controller.start(60_000L)
        advanceTimeBy(50_000L)
        runCurrent()
        assertTrue(volumes.isNotEmpty())

        controller.cancel()

        assertEquals(USER_VOLUME, volumes.last(), 0.0001f)
        assertEquals(SleepTimer.Off, controller.state.value)
    }

    @Test
    fun `end of talk stops without ducking anything`() = runTest {
        val volumes = mutableListOf<Float>()
        val player = fakePlayer(volumes)
        val controller = SleepTimerController()
        controller.attach(player, backgroundScope)

        controller.startEndOfTrack()
        advanceTimeBy(10 * 60_000L)
        runCurrent()

        assertEquals(SleepTimer.EndOfTrack, controller.state.value)
        assertTrue("the recording ends on its own; nothing to fade", volumes.isEmpty())

        controller.onTrackEnded()

        verify { player.pause() }
        assertEquals(SleepTimer.Off, controller.state.value)
    }

    private companion object {
        const val USER_VOLUME = 1f
    }
}
