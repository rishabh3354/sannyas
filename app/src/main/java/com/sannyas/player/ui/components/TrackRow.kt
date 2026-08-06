package com.sannyas.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sannyas.player.R
import com.sannyas.player.core.common.compactDuration
import com.sannyas.player.core.common.spokenDuration
import com.sannyas.player.core.designsystem.component.NowPlayingIndicator
import com.sannyas.player.core.designsystem.component.OshoIconButton
import com.sannyas.player.core.designsystem.component.pressScale
import com.sannyas.player.core.designsystem.component.rememberInteraction
import com.sannyas.player.core.designsystem.theme.OshoShapeTokens
import com.sannyas.player.domain.model.DownloadState
import com.sannyas.player.domain.model.DownloadStatus
import com.sannyas.player.domain.model.Track

/**
 * A single talk in a list.
 *
 * Carries a lot of state — playing, resume position, download progress — so the
 * layout keeps a strict left-to-right hierarchy: index or equaliser, then
 * title and metadata, then download affordance, then overflow. The whole row is
 * one semantics node so TalkBack reads it as a sentence rather than six labels.
 */
@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    resumeFraction: Float = 0f,
    downloadStatus: DownloadStatus? = null,
    onDownloadClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
) {
    val interaction = rememberInteraction()
    val highlight by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0f,
        animationSpec = tween(240),
        label = "row-highlight",
    )

    val duration = compactDuration(track.durationSeconds)
    val spokenState = when {
        isCurrent && isPlaying -> stringResource(R.string.cd_now_playing) + " "
        isCurrent -> stringResource(R.string.cd_paused) + " "
        else -> ""
    }
    val playLabel = stringResource(R.string.cd_play_item, track.title)
    val rowDescription = spokenState + stringResource(
        R.string.cd_track_row,
        track.title,
        spokenDuration(track.durationSeconds),
    )
    val moreLabel = stringResource(R.string.cd_more_options, track.title)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, pressedScale = 0.985f)
            .clip(OshoShapeTokens.Card)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f * highlight),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = playLabel,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { contentDescription = rowDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Leading slot: the equaliser replaces the number for the active track,
        // which reads faster than tinting the text.
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isCurrent) {
                NowPlayingIndicator(isPlaying = isPlaying)
            } else {
                Text(
                    text = track.number.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics { },
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = duration,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics { },
                )

                if (downloadStatus?.state == DownloadState.DOWNLOADED) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }

            // Resume bar appears only when there is something to resume, so a
            // fresh series shows a clean list.
            AnimatedVisibility(
                visible = resumeFraction > 0.01f && !isCurrent,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                LinearProgressIndicator(
                    progress = { resumeFraction },
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(3.dp)
                        .clip(OshoShapeTokens.Pill),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }

        onDownloadClick?.let { onDownload ->
            DownloadAffordance(status = downloadStatus, onClick = onDownload)
        }

        onMoreClick?.let {
            OshoIconButton(
                icon = Icons.Rounded.MoreVert,
                contentDescription = moreLabel,
                onClick = it,
                iconSize = 20.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Download button that doubles as a progress readout. */
@Composable
private fun DownloadAffordance(
    status: DownloadStatus?,
    onClick: () -> Unit,
) {
    when (status?.state) {
        DownloadState.DOWNLOADING, DownloadState.QUEUED -> {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (status.state == DownloadState.QUEUED || status.progress <= 0f) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { status.progress },
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                    )
                }
            }
        }

        DownloadState.DOWNLOADED -> OshoIconButton(
            icon = Icons.Rounded.CheckCircle,
            contentDescription = stringResource(R.string.cd_downloaded_remove),
            onClick = onClick,
            iconSize = 20.dp,
            tint = MaterialTheme.colorScheme.tertiary,
        )

        DownloadState.FAILED -> OshoIconButton(
            icon = Icons.Rounded.ErrorOutline,
            contentDescription = stringResource(R.string.cd_download_failed_retry),
            onClick = onClick,
            iconSize = 20.dp,
            tint = MaterialTheme.colorScheme.error,
        )

        else -> OshoIconButton(
            icon = Icons.Rounded.Download,
            contentDescription = stringResource(R.string.cd_download),
            onClick = onClick,
            iconSize = 20.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
