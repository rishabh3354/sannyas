package com.sannyas.player.core.review

import android.app.Activity
import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Asks for a Play rating, once, at the only moment worth asking.
 *
 * Deliberately unreachable from app start: [pending] can only be raised by
 * [onTalkCompleted], so the prompt always lands on someone who has just heard a
 * discourse through to its end rather than on someone who has just opened the
 * app to find something.
 *
 * The Play flow itself is quiet by design — it may show nothing at all, and it
 * never reports whether a review was left. So the cooldown is recorded on
 * *asking*, not on any outcome, and nothing here waits on a result.
 */
@Singleton
class ReviewPrompter @Inject constructor(
    private val context: Context,
) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _pending = MutableStateFlow(false)

    /**
     * True when a prompt is due. Collected with a lifecycle so it can only be
     * acted on while someone is actually looking at the app.
     */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    /** A talk played through to its end. */
    fun onTalkCompleted() {
        val completed = prefs.getInt(KEY_COMPLETED, 0) + 1
        prefs.edit().putInt(KEY_COMPLETED, completed).apply()

        if (ReviewPolicy.shouldPrompt(
                completedTalks = completed,
                lastPromptedAt = prefs.getLong(KEY_LAST_PROMPTED, 0L),
                now = System.currentTimeMillis(),
            )
        ) {
            _pending.value = true
        }
    }

    /**
     * Runs the Play flow. Never throws: no Play Store, an old Play version or a
     * quota refusal are all ordinary outcomes, and none of them are worth
     * bothering a listener about.
     */
    suspend fun launchFlow(activity: Activity) {
        _pending.value = false
        // Recorded up front. If the flow silently declines to show, asking
        // again tomorrow would be worse than waiting out the cooldown.
        prefs.edit().putLong(KEY_LAST_PROMPTED, System.currentTimeMillis()).apply()

        runCatching {
            val manager = ReviewManagerFactory.create(context)
            val info = manager.requestReviewFlow().await() ?: return@runCatching
            manager.launchReviewFlow(activity, info).await()
        }
    }

    /** Awaits a Play [Task] without pulling in the coroutines-play-services artifact. */
    private suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            continuation.resume(if (task.isSuccessful) task.result else null)
        }
    }

    private companion object {
        const val PREFS = "osho_review"
        const val KEY_COMPLETED = "completed_talks"
        const val KEY_LAST_PROMPTED = "last_prompted_at"
    }
}
