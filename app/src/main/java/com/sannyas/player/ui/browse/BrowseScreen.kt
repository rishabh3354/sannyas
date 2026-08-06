package com.sannyas.player.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sannyas.player.R
import com.sannyas.player.core.designsystem.component.AuroraBackground
import com.sannyas.player.core.designsystem.component.verticalScrollbar
import com.sannyas.player.core.designsystem.component.CategoryCard
import com.sannyas.player.core.designsystem.component.EmptyState
import com.sannyas.player.core.designsystem.component.ListSkeleton
import com.sannyas.player.core.designsystem.component.OshoIconButton
import com.sannyas.player.ui.navigation.BrowseKind

/**
 * The full list of themes, or of categories.
 *
 * A two-column grid of the same tiles the home rail uses, so arriving here from
 * "See all" reads as the row unrolling rather than as a different screen.
 */
@Composable
fun BrowseScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onTopicClick: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberLazyGridState()

    Box(modifier = modifier.fillMaxSize()) {
        AuroraBackground(modifier = Modifier.fillMaxSize(), alpha = 0.22f)

        Column(Modifier.fillMaxSize()) {
            BrowseTopBar(
                title = stringResource(state.kind.titleRes),
                subtitle = stringResource(state.kind.subtitleRes),
                onBack = onBack,
            )

            when {
                state.isLoading -> ListSkeleton(Modifier.padding(top = 24.dp))

                state.isEmpty -> EmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = stringResource(R.string.library_empty),
                    message = stringResource(R.string.collection_empty_generic),
                )

                else -> LazyVerticalGrid(
                    state = scrollState,
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().verticalScrollbar(scrollState),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 8.dp,
                        bottom = contentPadding.calculateBottomPadding() + 24.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.tiles, key = { it.id }) { tile ->
                        CategoryCard(
                            name = tile.name,
                            count = tile.seriesCount,
                            onClick = {
                                when (state.kind) {
                                    BrowseKind.TOPICS -> onTopicClick(tile.id)
                                    BrowseKind.CATEGORIES -> onCategoryClick(tile.id)
                                }
                            },
                            // Let the grid decide the width instead of the
                            // carousel default.
                            width = null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseTopBar(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 6.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OshoIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.cd_back),
            onClick = onBack,
        )
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
