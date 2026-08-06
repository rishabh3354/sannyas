package com.osho.player.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.North
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osho.player.R
import com.osho.player.core.common.compactDuration
import com.osho.player.core.common.seriesSubtitle
import com.osho.player.core.common.spokenDuration
import com.osho.player.core.common.talkCount
import com.osho.player.core.designsystem.component.Artwork
import com.osho.player.core.designsystem.component.AuroraBackground
import com.osho.player.core.designsystem.component.verticalScrollbar
import com.osho.player.core.designsystem.component.EmptyState
import com.osho.player.core.designsystem.component.OshoIconButton
import com.osho.player.core.designsystem.component.pressScale
import com.osho.player.core.designsystem.component.rememberInteraction
import com.osho.player.core.designsystem.theme.OshoShapeTokens
import com.osho.player.domain.model.Category
import com.osho.player.domain.model.Discourse
import com.osho.player.domain.model.Track

/**
 * Search.
 *
 * Three states share one scroll container: the browse state (recent searches
 * plus every category) when the field is empty, live suggestions while typing,
 * and grouped results once matches exist. Keeping them in one `LazyColumn`
 * means the transition never re-lays-out the whole screen.
 */
@Composable
fun SearchScreen(
    contentPadding: PaddingValues,
    onDiscourseClick: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberLazyListState()

    // Opening the Search tab should put the caret in the field with the keyboard
    // already up — arriving here is itself the intent to type. Keyed on Unit so
    // it fires once per visit, not on every recomposition.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AuroraBackground(modifier = Modifier.fillMaxSize(), alpha = 0.22f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            SearchField(
                focusRequester = focusRequester,
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onClear = viewModel::clearQuery,
                onSubmit = {
                    viewModel.onSearchSubmitted()
                    keyboard?.hide()
                },
            )

            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize().verticalScrollbar(scrollState),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!state.hasQuery) {
                    browseContent(
                        recentSearches = state.recentSearches,
                        categories = state.browseCategories,
                        onSelectRecent = viewModel::onSuggestionSelected,
                        onRemoveRecent = viewModel::removeRecentSearch,
                        onClearRecents = viewModel::clearRecentSearches,
                        onCategoryClick = onCategoryClick,
                    )
                } else {
                    if (state.suggestions.isNotEmpty() && state.results.isEmpty) {
                        suggestionContent(
                            suggestions = state.suggestions,
                            onSelect = {
                                viewModel.onSuggestionSelected(it)
                                keyboard?.hide()
                            },
                        )
                    }

                    resultContent(
                        state = state,
                        onDiscourseClick = { id ->
                            viewModel.onResultOpened(id)
                            onDiscourseClick(id)
                        },
                        onCategoryClick = onCategoryClick,
                        onTrackClick = viewModel::playTrack,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.showEmptyResults,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            EmptyState(
                icon = Icons.Rounded.SearchOff,
                title = stringResource(R.string.search_empty_title),
                message = stringResource(R.string.search_empty_message, state.query),
            )
        }
    }
}

@Composable
private fun SearchField(
    focusRequester: FocusRequester,
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(OshoShapeTokens.Pill)
            .focusRequester(focusRequester),
        placeholder = {
            Text(
                text = stringResource(R.string.search_placeholder),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                OshoIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cd_clear_search),
                    onClick = onClear,
                    iconSize = 20.dp,
                )
            }
        },
        singleLine = true,
        shape = OshoShapeTokens.Pill,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

// ------------------------------------------------------------------ sections

private fun androidx.compose.foundation.lazy.LazyListScope.browseContent(
    recentSearches: List<String>,
    categories: List<Category>,
    onSelectRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
    onCategoryClick: (String) -> Unit,
) {
    if (recentSearches.isNotEmpty()) {
        item(key = "recent-header") {
            SectionRow(title = stringResource(R.string.search_recent), actionLabel = stringResource(R.string.action_clear), onAction = onClearRecents)
        }
        items(recentSearches, key = { "recent-$it" }) { entry ->
            RecentSearchRow(
                text = entry,
                onClick = { onSelectRecent(entry) },
                onRemove = { onRemoveRecent(entry) },
            )
        }
        item(key = "recent-spacer") { Spacer(Modifier.height(12.dp)) }
    }

    if (categories.isNotEmpty()) {
        item(key = "browse-header") {
            SectionRow(title = stringResource(R.string.search_browse), actionLabel = null, onAction = null)
        }
        item(key = "browse-grid") {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                categories.forEach { category ->
                    CategoryChip(
                        name = category.name,
                        count = category.discourseCount,
                        onClick = { onCategoryClick(category.name) },
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.suggestionContent(
    suggestions: List<String>,
    onSelect: (String) -> Unit,
) {
    items(suggestions, key = { "suggest-$it" }) { suggestion ->
        SuggestionRow(text = suggestion, onClick = { onSelect(suggestion) })
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.resultContent(
    state: SearchUiState,
    onDiscourseClick: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    onTrackClick: (String) -> Unit,
) {
    val results = state.results

    if (results.categories.isNotEmpty()) {
        item(key = "res-cat-header") {
            SectionRow(title = stringResource(R.string.search_section_categories), actionLabel = null, onAction = null)
        }
        item(key = "res-cat") {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                results.categories.forEach { category ->
                    CategoryChip(
                        name = category.name,
                        count = category.discourseCount,
                        onClick = { onCategoryClick(category.name) },
                    )
                }
            }
        }
    }

    if (results.discourses.isNotEmpty()) {
        item(key = "res-series-header") {
            SectionRow(title = stringResource(R.string.search_section_series), actionLabel = null, onAction = null)
        }
        items(results.discourses, key = { "series-${it.id}" }) { discourse ->
            DiscourseResultRow(
                discourse = discourse,
                onClick = { onDiscourseClick(discourse.id) },
            )
        }
    }

    if (results.tracks.isNotEmpty()) {
        item(key = "res-track-header") {
            SectionRow(title = stringResource(R.string.search_section_talks), actionLabel = null, onAction = null)
        }
        items(results.tracks, key = { "track-${it.id}" }) { track ->
            TrackResultRow(track = track, onClick = { onTrackClick(track.id) })
        }
    }
}

// -------------------------------------------------------------------- rows

@Composable
private fun SectionRow(title: String, actionLabel: String?, onAction: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .sizeIn(minHeight = 44.dp)
                    .clip(OshoShapeTokens.Pill)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun RecentSearchRow(text: String, onClick: () -> Unit, onRemove: () -> Unit) {
    val removeLabel = stringResource(R.string.cd_remove_recent_search, text)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        OshoIconButton(
            icon = Icons.Rounded.Close,
            contentDescription = removeLabel,
            onClick = onRemove,
            iconSize = 18.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SuggestionRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            // Points up-left: tapping fills the field rather than running a search.
            imageVector = Icons.Rounded.North,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun DiscourseResultRow(discourse: Discourse, onClick: () -> Unit) {
    val interaction = rememberInteraction()
    val openLabel = stringResource(R.string.cd_open, discourse.title)
    val talks = talkCount(discourse.trackCount)
    val description = stringResource(
        R.string.cd_series_result,
        discourse.title,
        talks,
        discourse.category,
    )
    val subtitle = stringResource(
        R.string.dot_separated,
        discourse.category,
        seriesSubtitle(discourse.trackCount, discourse.durationSeconds),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .pressScale(interaction, pressedScale = 0.99f)
            .clip(OshoShapeTokens.Card)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = openLabel,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            imageUrl = discourse.image,
            contentDescription = null,
            fallbackKey = discourse.id,
            fallbackLabel = discourse.title,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = discourse.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TrackResultRow(track: Track, onClick: () -> Unit) {
    val interaction = rememberInteraction()
    val playLabel = stringResource(R.string.cd_play_item, track.title)
    val description = stringResource(
        R.string.cd_track_result,
        track.title,
        track.discourseTitle,
        spokenDuration(track.durationSeconds),
    )
    val subtitle = stringResource(
        R.string.dot_separated,
        track.discourseTitle,
        compactDuration(track.durationSeconds),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .pressScale(interaction, pressedScale = 0.99f)
            .clip(OshoShapeTokens.Card)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = playLabel,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            imageUrl = track.artworkUrl,
            contentDescription = null,
            fallbackKey = track.discourseId,
            fallbackLabel = track.discourseTitle,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CategoryChip(name: String, count: Int, onClick: () -> Unit) {
    val interaction = rememberInteraction()
    val browseLabel = stringResource(R.string.cd_browse, name)

    Row(
        modifier = Modifier
            .sizeIn(minHeight = 44.dp)
            .pressScale(interaction)
            .clip(OshoShapeTokens.Pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = browseLabel,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
