package com.sannyas.player

import com.sannyas.player.domain.model.PlaybackProgress
import com.sannyas.player.domain.model.PlayerState
import com.sannyas.player.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These rules decide what appears in Continue Listening, which is the first
 * thing a returning listener sees — a wrong threshold is very visible.
 */
class PlaybackProgressTest {

    private fun progress(positionMs: Long, durationMs: Long = 60 * 60 * 1000L) =
        PlaybackProgress(
            trackId = "series#1",
            discourseId = "series",
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAt = 0L,
        )

    @Test
    fun `a brief accidental tap is not resumable`() {
        assertFalse(progress(10_000).isMeaningful)
    }

    @Test
    fun `a real listening session is resumable`() {
        assertTrue(progress(15 * 60_000).isMeaningful)
    }

    @Test
    fun `a talk within the completion tolerance counts as finished`() {
        val nearEnd = progress(60 * 60 * 1000L - 30_000)
        assertTrue(nearEnd.isCompleted)
        // Finished talks must drop out of Continue Listening.
        assertFalse(nearEnd.isMeaningful)
    }

    @Test
    fun `fraction is clamped and safe when the duration is unknown`() {
        assertEquals(0f, progress(5_000, durationMs = 0).fraction, 0f)
        assertEquals(1f, progress(999_999, durationMs = 1_000).fraction, 0f)
    }

    @Test
    fun `player progress is zero rather than NaN before the duration is known`() {
        val state = PlayerState(positionMs = 5_000, durationMs = 0)
        assertEquals(0f, state.progress, 0f)
        assertEquals(0f, state.bufferedProgress, 0f)
    }

    @Test
    fun `player is only active once a track is loaded`() {
        assertFalse(PlayerState().isActive)
        assertTrue(PlayerState(currentTrack = track()).isActive)
    }

    private fun track() = Track(
        id = "series#1",
        discourseId = "series",
        title = "Talk 01",
        url = "https://example.invalid/1.mp3",
        durationSeconds = 3_600,
        index = 1,
        number = 1,
        artworkUrl = "",
        discourseTitle = "Series",
        speaker = "Osho",
    )
}
