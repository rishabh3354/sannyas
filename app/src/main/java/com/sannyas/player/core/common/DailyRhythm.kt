package com.sannyas.player.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * The date-driven bits of the home screen.
 *
 * Both are pure so they can be tested without a clock or a database — they are
 * the sort of thing that silently drifts by a day at a timezone boundary and is
 * never noticed until someone loses a streak they had earned.
 */
object DailyRhythm {

    /**
     * Picks one index out of [size], the same one all day and the same one for
     * everybody.
     *
     * Seeded by the date alone — no device id, no install time — so two people
     * comparing notes get the same talk. It follows that the pick shifts if the
     * pool changes size, which is the price of not storing a chosen id
     * anywhere.
     */
    fun indexForDay(epochDay: Long, size: Int): Int {
        if (size <= 0) return -1
        return Random(epochDay).nextInt(size)
    }

    /**
     * Consecutive days ending today on which something was played.
     *
     * Deliberately forgiving about today: a streak stays alive until the day is
     * actually over, so opening the app at breakfast never reports a loss that
     * an evening's listening would have prevented. Counting stops at the first
     * gap.
     */
    fun streakDays(
        playedAtMillis: Collection<Long>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        if (playedAtMillis.isEmpty()) return 0

        val days = playedAtMillis
            .mapTo(HashSet()) { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

        // Yesterday still counts as the head of a live streak; the day has not
        // run out yet.
        var cursor = when {
            today in days -> today
            today.minusDays(1) in days -> today.minusDays(1)
            else -> return 0
        }

        var count = 0
        while (cursor in days) {
            count += 1
            cursor = cursor.minusDays(1)
        }
        return count
    }
}
