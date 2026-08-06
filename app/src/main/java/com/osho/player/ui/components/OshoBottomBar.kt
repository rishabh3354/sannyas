package com.osho.player.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.osho.player.core.designsystem.component.rememberInteraction
import com.osho.player.core.designsystem.theme.OshoShapeTokens
import com.osho.player.ui.navigation.TopLevelDestination

/**
 * Bottom navigation.
 *
 * Deliberately not `NavigationBar`: the stock component paints an opaque
 * surface, and this design needs the aurora and artwork to show through. The
 * selected tab gets a soft pill behind it and a springy icon scale, which is
 * enough signal without the heavy Material indicator.
 */
@Composable
fun OshoBottomBar(
    destinations: List<TopLevelDestination>,
    currentRoute: String?,
    onNavigate: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                // Fades the content out beneath the bar instead of cutting it
                // off with a hard edge.
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                BottomBarItem(
                    destination = destination,
                    selected = currentRoute == destination.route,
                    onClick = { onNavigate(destination) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = rememberInteraction()
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tab-scale",
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(),
        label = "tab-indicator",
    )

    val label = stringResource(destination.labelRes)

    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .sizeIn(minHeight = 56.dp)
            .clip(OshoShapeTokens.Pill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = label
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(width = 46.dp, height = 30.dp)
                    .clip(OshoShapeTokens.Pill)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f * indicatorAlpha),
                    ),
            )
            Icon(
                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(23.dp)
                    .scale(iconScale),
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            // Five tabs leave roughly 70dp each on a small phone, and the
            // longest Hindi label ("लाइब्रेरी") overruns that. Clipping to one
            // line keeps every tab the same height rather than letting one
            // wrap and push the bar taller.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .clearAndSetSemantics { },
        )
    }
}

/** Height of the bar's content, used to size the scaffold's bottom inset. */
val BottomBarHeight = 66.dp

@Composable
fun BottomBarSpacer() {
    Spacer(Modifier.height(BottomBarHeight))
}
