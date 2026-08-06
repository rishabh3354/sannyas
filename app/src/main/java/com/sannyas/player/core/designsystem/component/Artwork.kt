package com.sannyas.player.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sannyas.player.core.designsystem.theme.OshoColors
import com.sannyas.player.core.designsystem.theme.OshoShapeTokens

/**
 * Series artwork with a designed fallback.
 *
 * Remote artwork can be slow or missing, so while it resolves — and permanently
 * if it fails — this paints a deterministic gradient derived from [fallbackKey]
 * with the series initials on top. Two things follow: a scrolling grid never
 * flashes grey boxes, and a given series always gets the same colour, which is
 * what makes the library feel designed rather than generated.
 */
@Composable
fun Artwork(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = OshoShapeTokens.Artwork,
    fallbackKey: String = imageUrl.orEmpty(),
    fallbackLabel: String = "",
) {
    val accent = remember(fallbackKey) { OshoColors.accentFor(fallbackKey) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(accent.first.copy(alpha = 0.9f), accent.second)),
            )
            // One description on the container, so TalkBack announces the
            // artwork once instead of also reading the fallback initials.
            .semantics {
                contentDescription?.let { this.contentDescription = it }
            },
    ) {
        // The fallback sits permanently underneath rather than being swapped in.
        // That matters for more than looks: Coil resolves a request's target size
        // from the composable actually being drawn, so gating the image behind a
        // success check would leave it unmeasured and the request cancelled
        // before it ever ran. Drawing it always, over the fallback, is what makes
        // loading work at all — and it doubles as the placeholder.
        ArtworkFallback(label = fallbackLabel)

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl?.takeIf { it.isNotBlank() })
                .crossfade(CROSSFADE_MILLIS)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

private const val CROSSFADE_MILLIS = 260

@Composable
private fun ArtworkFallback(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (label.isNotBlank()) {
            Text(
                text = label.initials(),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White.copy(alpha = 0.82f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Up to two initials, skipping the articles that open many series titles. */
private fun String.initials(): String {
    val skip = setOf("a", "an", "the", "of", "on", "in", "and", "to")
    return trim()
        .split(' ')
        .filter { it.isNotBlank() && it.lowercase() !in skip }
        .take(2)
        .mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { trim().take(1).uppercase() }
}
