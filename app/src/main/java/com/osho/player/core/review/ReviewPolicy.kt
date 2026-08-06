package com.osho.player.core.review

import java.util.concurrent.TimeUnit

/**
 * When it is reasonable to ask for a rating.
 *
 * Pure and separate from the Play plumbing so the rules — the part that decides
 * whether this is a courtesy or a nuisance — can be tested without an Activity.
 *
 * The one rule that matters most is not expressed here because it is structural:
 * nothing calls this on launch. The prompt is only ever reachable from having
 * just finished a talk, which is the moment someone actually has an opinion
 * worth recording.
 */
internal object ReviewPolicy {

    /** Enough listening to have formed a view; not so much that we missed the moment. */
    const val MIN_COMPLETED_TALKS = 3

    /** Play throttles its own quota, but asking twice a year is our own limit. */
    val COOLDOWN_MS: Long = TimeUnit.DAYS.toMillis(120)

    fun shouldPrompt(
        completedTalks: Int,
        lastPromptedAt: Long,
        now: Long,
    ): Boolean {
        if (completedTalks < MIN_COMPLETED_TALKS) return false
        if (lastPromptedAt <= 0L) return true
        // A clock that has gone backwards (timezone edit, restored backup)
        // should not unlock a fresh prompt.
        if (now < lastPromptedAt) return false
        return now - lastPromptedAt >= COOLDOWN_MS
    }
}
