package com.sannyas.player.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sannyas.player.R
import com.sannyas.player.core.common.remainingLabel
import com.sannyas.player.core.designsystem.theme.OshoColors
import com.sannyas.player.core.designsystem.theme.OshoShapeTokens

/**
 * The standard carousel card: large artwork above two lines of text.
 *
 * Sized by [width] rather than a weight so several carousels on the home screen
 * share one rhythm and the next card always peeks in from the right edge,
 * which is what invites the horizontal scroll.
 */
@Composable
fun DiscourseCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Fixed card width for carousels; pass null to let a grid size the card. */
    width: Dp? = 168.dp,
) {
    val interaction = rememberInteraction()
    val openLabel = stringResource(R.string.cd_open, title)
    val description = stringResource(R.string.cd_item_detail, title, subtitle)

    Column(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .pressScale(interaction)
            // No card-wide clip: a 24dp radius curves in far enough at the
            // bottom corners to shave the first glyph off the subtitle ("6 talks"
            // losing its 6). The artwork carries its own rounding, and the card
            // has no background to shape, so nothing needs clipping here.
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = openLabel,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Artwork(
            imageUrl = imageUrl,
            contentDescription = null,
            fallbackKey = title,
            fallbackLabel = title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .softShadow(OshoShapeTokens.Artwork, elevation = 14.dp),
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/**
 * The wide "Continue Listening" card: artwork on the left, metadata and a
 * resume bar on the right.
 */
@Composable
fun ContinueListeningCard(
    title: String,
    seriesTitle: String,
    imageUrl: String?,
    positionMs: Long,
    durationMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Fixed width for carousels; pass null to let a list size the card. */
    width: Dp? = 300.dp,
) {
    val interaction = rememberInteraction()
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val remaining = remainingLabel(positionMs, durationMs)
    val resumeLabel = stringResource(R.string.cd_resume_item, title)
    val description = stringResource(R.string.cd_continue_card, title, seriesTitle, remaining)

    Row(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .pressScale(interaction)
            .glassSurface(OshoShapeTokens.Card)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = resumeLabel,
                onClick = onClick,
            )
            .padding(10.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            imageUrl = imageUrl,
            contentDescription = null,
            fallbackKey = seriesTitle,
            fallbackLabel = seriesTitle,
            shape = OshoShapeTokens.Artwork,
            modifier = Modifier.size(72.dp),
        )

        Spacer(Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics { },
            )
            Text(
                text = remaining,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics { },
            )
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(OshoShapeTokens.Pill),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

/**
 * The tall editorial card used by the "Popular" and "Trending" rails — artwork
 * fills the card and the title sits over a scrim.
 */
@Composable
fun FeatureCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = rememberInteraction()
    val openLabel = stringResource(R.string.cd_open, title)
    val description = stringResource(R.string.cd_item_detail, title, subtitle)

    Box(
        modifier = modifier
            .width(230.dp)
            .height(300.dp)
            .pressScale(interaction)
            .softShadow(OshoShapeTokens.HeroCard, elevation = 20.dp)
            .clip(OshoShapeTokens.HeroCard)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = openLabel,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Artwork(
            imageUrl = imageUrl,
            contentDescription = null,
            fallbackKey = title,
            fallbackLabel = title,
            shape = OshoShapeTokens.HeroCard,
            modifier = Modifier.fillMaxSize(),
        )

        Box(Modifier.fillMaxSize().verticalScrim(startFraction = 0.42f))

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics { },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/** Compact gradient tile for the Categories rail. */
@Composable
fun CategoryCard(
    name: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Fixed width for carousels; pass null to let a grid size the tile. */
    width: Dp? = 180.dp,
) {
    val interaction = rememberInteraction()
    val accent = OshoColors.accentFor(name)
    val label = pluralStringResource(R.plurals.series_count, count, count)
    val browseLabel = stringResource(R.string.cd_browse, name)
    val description = stringResource(R.string.cd_item_detail, name, label)

    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .height(96.dp)
            .pressScale(interaction)
            .clip(OshoShapeTokens.Card)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = browseLabel,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(accentBrush(accent)),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics { },
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/**
 * Station card for the Radio rail.
 *
 * Deliberately artwork-free: a station is a stream, not a release, so a cover
 * would imply a specific series. The accent gradient plus a broadcast mark
 * reads as "this plays forever" rather than "this is an album".
 */
@Composable
fun StationCard(
    name: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Fixed width for carousels; pass null to let a grid size the card. */
    width: Dp? = 200.dp,
) {
    val interaction = rememberInteraction()
    val accent = OshoColors.accentFor(name)
    val playLabel = stringResource(R.string.cd_play_item, name)
    val description = stringResource(R.string.cd_station, name, detail)

    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .height(132.dp)
            .pressScale(interaction)
            .softShadow(OshoShapeTokens.Card, elevation = 14.dp)
            .clip(OshoShapeTokens.Card)
            .background(accentBrush(accent))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = playLabel,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Icon(
            imageVector = Icons.Rounded.Podcasts,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
                .size(22.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics { },
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}
