package com.osho.player.core.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.osho.player.R

/**
 * Runtime and count wording, in whatever language the app is set to.
 *
 * These are separate from [DurationFormat] because they need resources: the
 * unit abbreviations differ per language, and "12 talks" pluralises. Every
 * helper has a `Context` form for the media session and services, plus a
 * composable form so screens can call it without threading a context through.
 */

/** `1h 04m` / `47 min` — used on cards where a clock would read as a timestamp. */
fun compactDuration(context: Context, seconds: Long): String {
    val (hours, minutes) = DurationFormat.hoursAndMinutes(seconds)
    return when {
        hours > 0 && minutes > 0 ->
            context.getString(R.string.duration_hours_minutes, hours, minutes)

        hours > 0 -> context.getString(R.string.duration_hours, hours)
        else -> context.getString(R.string.duration_minutes, minutes)
    }
}

@Composable
fun compactDuration(seconds: Long): String = compactDuration(LocalContext.current, seconds)

/** Spoken form for TalkBack, e.g. "1 hour 4 minutes". */
fun spokenDuration(context: Context, seconds: Long): String {
    val (hours, minutes) = DurationFormat.hoursAndMinutes(seconds)
    val resources = context.resources
    return buildString {
        if (hours > 0) append(resources.getQuantityString(R.plurals.duration_spoken_hours, hours, hours))
        if (hours > 0 && minutes > 0) append(' ')
        if (minutes > 0) {
            append(resources.getQuantityString(R.plurals.duration_spoken_minutes, minutes, minutes))
        }
    }
}

@Composable
fun spokenDuration(seconds: Long): String = spokenDuration(LocalContext.current, seconds)

/** "23 min left" — the label under Continue Listening cards. */
@Composable
fun remainingLabel(positionMs: Long, durationMs: Long): String = stringResource(
    R.string.time_left,
    compactDuration(((durationMs - positionMs).coerceAtLeast(0L)) / 1000),
)

/** "12 talks" / "1 talk". */
fun talkCountText(context: Context, count: Int): String =
    context.resources.getQuantityString(R.plurals.talk_count, count, count)

@Composable
fun talkCount(count: Int): String = pluralStringResource(R.plurals.talk_count, count, count)

/** "12 talks · 1h 20m" — the standard two-fact subtitle under a series card. */
@Composable
fun seriesSubtitle(trackCount: Int, durationSeconds: Long): String = stringResource(
    R.string.dot_separated,
    talkCount(trackCount),
    compactDuration(durationSeconds),
)
