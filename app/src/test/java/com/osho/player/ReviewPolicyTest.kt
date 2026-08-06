package com.osho.player

import com.osho.player.core.review.ReviewPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * When it is reasonable to ask for a rating.
 *
 * These rules are the whole feature. Getting them wrong is worse than not
 * shipping it: a prompt at the wrong moment costs the exact rating it was
 * meant to earn.
 */
class ReviewPolicyTest {

    private val now = TimeUnit.DAYS.toMillis(500)

    @Test
    fun `stays quiet until someone has actually listened`() {
        assertFalse(ReviewPolicy.shouldPrompt(completedTalks = 0, lastPromptedAt = 0, now = now))
        assertFalse(ReviewPolicy.shouldPrompt(completedTalks = 1, lastPromptedAt = 0, now = now))
        assertFalse(ReviewPolicy.shouldPrompt(completedTalks = 2, lastPromptedAt = 0, now = now))
    }

    @Test
    fun `asks once enough talks have been finished`() {
        assertTrue(
            ReviewPolicy.shouldPrompt(
                completedTalks = ReviewPolicy.MIN_COMPLETED_TALKS,
                lastPromptedAt = 0,
                now = now,
            ),
        )
    }

    @Test
    fun `does not ask twice inside the cooldown`() {
        val askedYesterday = now - TimeUnit.DAYS.toMillis(1)
        assertFalse(ReviewPolicy.shouldPrompt(completedTalks = 40, lastPromptedAt = askedYesterday, now = now))
    }

    @Test
    fun `asks again once the cooldown has passed`() {
        val longAgo = now - ReviewPolicy.COOLDOWN_MS - 1
        assertTrue(ReviewPolicy.shouldPrompt(completedTalks = 40, lastPromptedAt = longAgo, now = now))
    }

    @Test
    fun `a backwards clock does not unlock a fresh prompt`() {
        // Restored backup, or a timezone edit: "now" can legitimately precede
        // the recorded prompt. That must not read as a lapsed cooldown.
        val future = now + TimeUnit.DAYS.toMillis(365)
        assertFalse(ReviewPolicy.shouldPrompt(completedTalks = 40, lastPromptedAt = future, now = now))
    }
}
