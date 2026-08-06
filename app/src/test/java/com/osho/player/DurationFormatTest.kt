package com.osho.player

import com.osho.player.core.common.DurationFormat
import com.osho.player.core.common.formatBytes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The catalog's durations are `HH:MM:SS` strings written by a generator script,
 * so parsing has to survive a hand-edited file as well as the generated one.
 */
class DurationFormatTest {

    @Test
    fun `parses hours minutes seconds`() {
        assertEquals(5869L, DurationFormat.parseToSeconds("01:37:49"))
    }

    @Test
    fun `parses minutes and seconds`() {
        assertEquals(229L, DurationFormat.parseToSeconds("3:49"))
    }

    @Test
    fun `parses a bare second count`() {
        assertEquals(90L, DurationFormat.parseToSeconds("90"))
    }

    @Test
    fun `returns zero for blank or malformed input`() {
        assertEquals(0L, DurationFormat.parseToSeconds(null))
        assertEquals(0L, DurationFormat.parseToSeconds(""))
        assertEquals(0L, DurationFormat.parseToSeconds("not a duration"))
    }

    @Test
    fun `clock drops the hour segment under an hour`() {
        assertEquals("4:09", DurationFormat.clock(249_000))
        assertEquals("1:04:09", DurationFormat.clock(3_849_000))
    }

    @Test
    fun `clock never renders a negative position`() {
        assertEquals("0:00", DurationFormat.clock(-5_000))
    }

    @Test
    fun `splits a runtime into hours and minutes`() {
        assertEquals(1 to 37, DurationFormat.hoursAndMinutes(5_869))
        assertEquals(0 to 47, DurationFormat.hoursAndMinutes(2_820))
    }

    @Test
    fun `rounds a sub-minute talk up to one minute`() {
        assertEquals(0 to 1, DurationFormat.hoursAndMinutes(20))
    }

    @Test
    fun `keeps a whole-hour runtime free of stray minutes`() {
        assertEquals(2 to 0, DurationFormat.hoursAndMinutes(7_200))
    }

    @Test
    fun `byte formatting picks a sensible unit`() {
        assertEquals("512 B", 512L.formatBytes())
        assertEquals("1.0 KB", 1024L.formatBytes())
        assertEquals("2.5 MB", (2_621_440L).formatBytes())
    }
}
