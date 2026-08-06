package com.osho.player

import com.osho.player.core.common.DailyRhythm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyRhythmTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val today: LocalDate = LocalDate.of(2026, 8, 6)

    private fun at(date: LocalDate, hour: Int = 21): Long =
        ZonedDateTime.of(date.atTime(hour, 0), zone).toInstant().toEpochMilli()

    // ---- the daily pick ---------------------------------------------------

    @Test
    fun `the same day always gives the same talk`() {
        val day = today.toEpochDay()
        val first = DailyRhythm.indexForDay(day, 5_870)
        repeat(50) { assertEquals(first, DailyRhythm.indexForDay(day, 5_870)) }
    }

    @Test
    fun `a different day gives a different talk`() {
        val a = DailyRhythm.indexForDay(today.toEpochDay(), 5_870)
        val b = DailyRhythm.indexForDay(today.plusDays(1).toEpochDay(), 5_870)
        assertNotEquals(a, b)
    }

    @Test
    fun `always lands inside the pool`() {
        // Whatever the catalogue and the language filter leave behind.
        listOf(1, 2, 7, 173, 5_870).forEach { size ->
            repeat(400) { offset ->
                val index = DailyRhythm.indexForDay(today.toEpochDay() + offset, size)
                assertTrue("index $index out of range for size $size", index in 0 until size)
            }
        }
    }

    @Test
    fun `an empty pool asks for nothing`() {
        assertEquals(-1, DailyRhythm.indexForDay(today.toEpochDay(), 0))
    }

    // ---- the streak -------------------------------------------------------

    @Test
    fun `no listening is no streak`() {
        assertEquals(0, DailyRhythm.streakDays(emptyList(), today, zone))
    }

    @Test
    fun `counts consecutive days ending today`() {
        val played = (0..3).map { at(today.minusDays(it.toLong())) }
        assertEquals(4, DailyRhythm.streakDays(played, today, zone))
    }

    @Test
    fun `a streak survives a day that is not over yet`() {
        // Listened yesterday, nothing today: opening the app at breakfast must
        // not report the streak as lost.
        val played = (1..3).map { at(today.minusDays(it.toLong())) }
        assertEquals(3, DailyRhythm.streakDays(played, today, zone))
    }

    @Test
    fun `a missed day ends the streak`() {
        val played = listOf(
            at(today),
            at(today.minusDays(1)),
            // gap on day 2
            at(today.minusDays(3)),
            at(today.minusDays(4)),
        )
        assertEquals(2, DailyRhythm.streakDays(played, today, zone))
    }

    @Test
    fun `listening twice in a day still counts once`() {
        val played = listOf(at(today, hour = 7), at(today, hour = 23), at(today.minusDays(1)))
        assertEquals(2, DailyRhythm.streakDays(played, today, zone))
    }

    @Test
    fun `an old streak that has lapsed counts for nothing`() {
        val played = (10..14).map { at(today.minusDays(it.toLong())) }
        assertEquals(0, DailyRhythm.streakDays(played, today, zone))
    }

    @Test
    fun `late-night listening belongs to the day it happened locally`() {
        // 23:30 local is the same local day, however it lands in UTC.
        val played = listOf(at(today.minusDays(1), hour = 23), at(today.minusDays(2), hour = 23))
        assertEquals(2, DailyRhythm.streakDays(played, today, zone))
    }
}
