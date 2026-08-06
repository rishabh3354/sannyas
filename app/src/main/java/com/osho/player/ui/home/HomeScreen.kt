package com.osho.player.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osho.player.R
import com.osho.player.core.common.compactDuration
import com.osho.player.core.common.seriesSubtitle
import com.osho.player.core.designsystem.component.Artwork
import com.osho.player.core.designsystem.component.AuroraBackground
import com.osho.player.core.designsystem.component.CarouselSkeleton
import com.osho.player.core.designsystem.component.CategoryCard
import com.osho.player.core.designsystem.component.ContinueListeningCard
import com.osho.player.core.designsystem.component.DiscourseCard
import com.osho.player.core.designsystem.component.EmptyState
import com.osho.player.core.designsystem.component.ErrorState
import com.osho.player.core.designsystem.component.FeatureCard
import com.osho.player.core.designsystem.component.OshoIconButton
import com.osho.player.core.designsystem.component.SectionHeader
import com.osho.player.core.designsystem.component.softShadow
import com.osho.player.core.designsystem.component.topScrim
import com.osho.player.core.designsystem.component.glassSurface
import com.osho.player.core.designsystem.component.pressScale
import com.osho.player.core.designsystem.component.rememberInteraction
import com.osho.player.core.designsystem.component.verticalScrollbar
import com.osho.player.core.designsystem.theme.LocalIsDarkTheme
import com.osho.player.core.designsystem.theme.OshoColors
import com.osho.player.core.designsystem.theme.OshoShapeTokens
import com.osho.player.core.locale.labelRes
import com.osho.player.domain.model.ContentLanguage
import com.osho.player.domain.model.Discourse
import com.osho.player.ui.navigation.BrowseKind
import com.osho.player.ui.navigation.CollectionKind

/**
 * Home.
 *
 * A single `LazyColumn` of horizontal rails. Each rail is its own item so the
 * column only composes what is on screen, and each `LazyRow` keeps its own
 * scroll position while the column recycles around it.
 */
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onDiscourseClick: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    onTopicClick: (String) -> Unit,
    onSeeAll: (CollectionKind) -> Unit,
    onBrowseAll: (BrowseKind) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showLanguagePicker by remember { mutableStateOf(false) }

    // The header fades and the compact title appears once the greeting scrolls
    // away, so the top of the screen is never empty chrome.
    val headerCollapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120 }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AuroraBackground(
            modifier = Modifier.fillMaxSize(),
            alpha = 0.35f,
        )

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.hasError -> ErrorState(
                    title = stringResource(R.string.home_error_title),
                    message = stringResource(R.string.home_error_generic),
                    onRetry = viewModel::retry,
                    modifier = Modifier.align(Alignment.Center),
                )

                state.isLoading -> HomeSkeleton(contentPadding)

                state.isEmpty -> EmptyState(
                    icon = Icons.Rounded.SelfImprovement,
                    title = stringResource(R.string.home_empty_title),
                    message = stringResource(R.string.home_empty_message),
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> HomeContent(
                    state = state,
                    listState = listState,
                    contentPadding = contentPadding,
                    onDiscourseClick = onDiscourseClick,
                    onCategoryClick = onCategoryClick,
                    onTopicClick = onTopicClick,
                    onSeeAll = onSeeAll,
                    onBrowseAll = onBrowseAll,
                    onResume = viewModel::resume,
                    onLanguageClick = { showLanguagePicker = true },
                    onPlayDaily = viewModel::playTalkOfTheDay,
                )
            }
        }

        TopScrim(
            titleVisible = headerCollapsed,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    if (showLanguagePicker) {
        AlertDialog(
            onDismissRequest = { showLanguagePicker = false },
            title = { Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    ContentLanguage.entries.forEach { option ->
                        val selected = option == state.contentLanguage
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .sizeIn(minHeight = 52.dp)
                                .clip(OshoShapeTokens.Pill)
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        viewModel.setContentLanguage(option)
                                        showLanguagePicker = false
                                    },
                                )
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(option.labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguagePicker = false }) { Text(stringResource(R.string.action_done)) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onDiscourseClick: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    onTopicClick: (String) -> Unit,
    onSeeAll: (CollectionKind) -> Unit,
    onBrowseAll: (BrowseKind) -> Unit,
    onResume: (ContinueItem) -> Unit,
    onLanguageClick: () -> Unit,
    onPlayDaily: (TalkOfTheDay) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().verticalScrollbar(listState),
        contentPadding = PaddingValues(
            top = 0.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        item(key = "greeting") {
            GreetingHeader(
                greeting = state.greeting,
                language = state.contentLanguage,
                streakDays = state.streakDays,
                onLanguageClick = onLanguageClick,
            )
        }

        state.talkOfTheDay?.let { daily ->
            item(key = "talk-of-day") {
                TalkOfTheDayCard(
                    item = daily,
                    onPlay = { onPlayDaily(daily) },
                    onOpen = { onDiscourseClick(daily.discourse.id) },
                )
            }
        }

        if (state.continueListening.isNotEmpty()) {
            item(key = "continue") {
                Rail(
                    title = stringResource(R.string.home_continue),
                    subtitle = stringResource(R.string.home_continue_subtitle),
                    onSeeAll = { onSeeAll(CollectionKind.CONTINUE_LISTENING) },
                ) {
                    items(state.continueListening, key = { it.track.id }) { item ->
                        ContinueListeningCard(
                            title = item.track.title,
                            seriesTitle = item.discourse.title,
                            imageUrl = item.discourse.image,
                            positionMs = item.positionMs,
                            durationMs = item.durationMs,
                            onClick = { onResume(item) },
                        )
                    }
                }
            }
        }

        if (state.popular.isNotEmpty()) {
            item(key = "popular") {
                Rail(
                    title = stringResource(R.string.home_popular),
                    subtitle = stringResource(R.string.home_popular_subtitle),
                    onSeeAll = { onSeeAll(CollectionKind.POPULAR) },
                ) {
                    items(state.popular, key = { it.id }) { discourse ->
                        FeatureCard(
                            title = discourse.title,
                            subtitle = seriesSubtitle(discourse.trackCount, discourse.durationSeconds),
                            imageUrl = discourse.image,
                            onClick = { onDiscourseClick(discourse.id) },
                        )
                    }
                }
            }
        }

        if (state.recentlyAdded.isNotEmpty()) {
            item(key = "recent") {
                DiscourseRail(
                    title = stringResource(R.string.home_recent),
                    subtitle = stringResource(R.string.home_recent_subtitle),
                    discourses = state.recentlyAdded,
                    onSeeAll = { onSeeAll(CollectionKind.RECENTLY_ADDED) },
                    onClick = onDiscourseClick,
                )
            }
        }

        if (state.meditation.isNotEmpty()) {
            item(key = "meditation") {
                DiscourseRail(
                    title = stringResource(R.string.home_meditation),
                    subtitle = stringResource(R.string.home_meditation_subtitle),
                    discourses = state.meditation,
                    onSeeAll = { onSeeAll(CollectionKind.MEDITATION) },
                    onClick = onDiscourseClick,
                )
            }
        }

        if (state.trending.isNotEmpty()) {
            item(key = "trending") {
                DiscourseRail(
                    title = stringResource(R.string.home_trending),
                    subtitle = stringResource(R.string.home_trending_subtitle),
                    discourses = state.trending,
                    onSeeAll = { onSeeAll(CollectionKind.TRENDING) },
                    onClick = onDiscourseClick,
                )
            }
        }

        if (state.recommended.isNotEmpty()) {
            item(key = "recommended") {
                DiscourseRail(
                    title = stringResource(R.string.home_recommended),
                    subtitle = stringResource(R.string.home_recommended_subtitle),
                    discourses = state.recommended,
                    onSeeAll = { onSeeAll(CollectionKind.RECOMMENDED) },
                    onClick = onDiscourseClick,
                )
            }
        }

        // The two browse rails close the feed. Everything above is editorial —
        // "here is something to play" — while these answer "show me the shape
        // of the library", which is what a listener reaches for only once the
        // suggestions have not landed. Each carries its own "See all".
        if (state.topics.isNotEmpty()) {
            item(key = "topics") {
                Rail(
                    title = stringResource(R.string.home_themes),
                    subtitle = stringResource(R.string.home_themes_subtitle),
                    onSeeAll = { onBrowseAll(BrowseKind.TOPICS) },
                ) {
                    items(state.topics, key = { it.id }) { topic ->
                        CategoryCard(
                            name = topic.name,
                            count = topic.seriesCount,
                            onClick = { onTopicClick(topic.id) },
                        )
                    }
                }
            }
        }

        if (state.categories.isNotEmpty()) {
            item(key = "categories") {
                Rail(
                    title = stringResource(R.string.home_categories),
                    subtitle = stringResource(R.string.home_categories_subtitle),
                    onSeeAll = { onBrowseAll(BrowseKind.CATEGORIES) },
                ) {
                    items(state.categories, key = { it.name }) { category ->
                        CategoryCard(
                            name = category.name,
                            count = category.discourseCount,
                            onClick = { onCategoryClick(category.name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GreetingHeader(
    greeting: Greeting,
    language: ContentLanguage,
    streakDays: Int,
    onLanguageClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(greeting.labelRes()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Stated once, in the same breath as the greeting, and never
            // celebrated. A single day is not a run worth naming.
            if (streakDays >= MIN_STREAK_TO_SHOW) {
                Text(
                    text = " · " + pluralStringResource(
                        R.plurals.streak_days,
                        streakDays,
                        streakDays,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        // Which half of the catalogue is on screen is otherwise invisible, and
        // it changes what every rail below contains — so it sits on the title
        // line, where it reads as a property of the library rather than a
        // stray control, and is the quickest place to change it.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            LanguageChip(language = language, onClick = onLanguageClick)
        }
    }
}

@Composable
private fun LanguageChip(language: ContentLanguage, onClick: () -> Unit) {
    val interaction = rememberInteraction()
    val languageLabel = stringResource(language.labelRes)
    val languageDescription = stringResource(R.string.cd_change_language) + ": " + languageLabel

    Row(
        modifier = Modifier
            .sizeIn(minHeight = 40.dp)
            .pressScale(interaction)
            .clip(OshoShapeTokens.Pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = stringResource(R.string.cd_change_language),
                onClick = onClick,
            )
            .padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = languageDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.RecordVoiceOver,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = languageLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Top scrim, and — in dark theme only — a compact title.
 *
 * Compose has no live backdrop blur, so this mirrors what the bottom bar does:
 * a vertical gradient out to transparent, which makes content dissolve as it
 * scrolls beneath the status bar rather than being cut off by a hard edge.
 *
 * Light theme drops the sticky title. The bottom bar gets away with a
 * see-through fade because nothing floats on top of it — its icons sit on their
 * own solid pill. Text needs opacity behind it to stay readable, and on a light
 * surface that opacity is exactly the heavy white slab we are trying to avoid.
 * Rather than compromise both, light theme keeps the pure fade and gives up the
 * scrolled-state title, which costs little: the app name is already on screen
 * at rest.
 */
@Composable
private fun TopScrim(titleVisible: Boolean, modifier: Modifier = Modifier) {
    // One value drives both the band and the title: at rest the scrim is fully
    // transparent so the greeting underneath it is visible, and it only becomes
    // an opaque bar once the greeting has scrolled away. Fading only the title
    // left the solid band sitting over "Good evening" from the first frame.
    val collapsed by animateFloatAsState(
        targetValue = if (titleVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "top-scrim",
    )
    val surface = MaterialTheme.colorScheme.surface
    val isDark = LocalIsDarkTheme.current

    // Dark theme can hold a solid band without drawing attention to itself. On a
    // light surface that same band reads as a heavy white slab, so light theme
    // uses the bottom bar's ramp mirrored — one short opaque run behind the
    // status bar, then straight out to transparent.
    val stops = if (isDark) {
        arrayOf(
            0.0f to surface.copy(alpha = collapsed),
            0.72f to surface.copy(alpha = collapsed),
            0.88f to surface.copy(alpha = 0.55f * collapsed),
            1.0f to Color.Transparent,
        )
    } else {
        arrayOf(
            0.0f to surface.copy(alpha = collapsed),
            0.45f to surface.copy(alpha = 0.86f * collapsed),
            1.0f to Color.Transparent,
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(colorStops = stops)),
    ) {
        Box(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = if (isDark) 22.dp else 40.dp),
        ) {
            if (isDark) {
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.alpha(collapsed),
                )
            } else {
                // Reserves the same band height so the fade keeps its run-out.
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

/**
 * Today's talk.
 *
 * Deliberately not another artwork-left glass row. Sitting directly above
 * Continue Listening, that shape read as the same component twice and the
 * daily pick lost all of its occasion. This is image-forward instead — the
 * cover fills the card, the wording sits over it — so the eye registers it as
 * a moment rather than as the first item of a list.
 */
@Composable
private fun TalkOfTheDayCard(
    item: TalkOfTheDay,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
) {
    val interaction = rememberInteraction()
    val accent = remember(item.discourse.id) { OshoColors.accentFor(item.discourse.id) }
    val playLabel = stringResource(R.string.cd_play_talk_of_day, item.track.title)
    val openLabel = stringResource(R.string.cd_open, item.discourse.title)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(196.dp)
            .pressScale(interaction, pressedScale = 0.99f)
            .softShadow(OshoShapeTokens.HeroCard, elevation = 20.dp)
            .clip(OshoShapeTokens.HeroCard)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = openLabel,
                onClick = onOpen,
            ),
    ) {
        Artwork(
            imageUrl = item.discourse.image,
            contentDescription = null,
            fallbackKey = item.discourse.id,
            fallbackLabel = item.discourse.title,
            shape = OshoShapeTokens.HeroCard,
            modifier = Modifier.fillMaxSize(),
        )

        // Darkens the top so the label reads whatever the photo does up there —
        // without it, a bright forehead swallowed the whole line.
        Box(Modifier.fillMaxSize().topScrim(alpha = 0.42f))

        // A wash of the series accent across the foot of the card, so the text
        // sits on colour rather than on whatever happens to be in the photo.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            accent.second.copy(alpha = 0.55f),
                            accent.second.copy(alpha = 0.92f),
                        ),
                    ),
                ),
        )

        Text(
            text = stringResource(R.string.home_talk_of_day).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(20.dp),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = item.track.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.dot_separated,
                        item.discourse.title,
                        compactDuration(item.track.durationSeconds),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(OshoShapeTokens.Pill)
                    .background(Color.White)
                    .clickable(role = Role.Button, onClickLabel = playLabel, onClick = onPlay)
                    .semantics { contentDescription = playLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

/** A titled horizontal rail. */
@Composable
private fun Rail(
    title: String,
    subtitle: String?,
    onSeeAll: (() -> Unit)?,
    content: LazyListScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(title = title, subtitle = subtitle, onSeeAll = onSeeAll)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            content = content,
        )
    }
}

@Composable
private fun DiscourseRail(
    title: String,
    subtitle: String?,
    discourses: List<Discourse>,
    onSeeAll: () -> Unit,
    onClick: (String) -> Unit,
) {
    Rail(title = title, subtitle = subtitle, onSeeAll = onSeeAll) {
        items(discourses, key = { it.id }) { discourse ->
            DiscourseCard(
                title = discourse.title,
                subtitle = seriesSubtitle(discourse.trackCount, discourse.durationSeconds),
                imageUrl = discourse.image,
                onClick = { onClick(discourse.id) },
            )
        }
    }
}

@Composable
private fun HomeSkeleton(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 24.dp, bottom = contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        CarouselSkeleton()
        CarouselSkeleton()
    }
}

private fun Greeting.labelRes(): Int = when (this) {
    Greeting.MORNING -> R.string.greeting_morning
    Greeting.AFTERNOON -> R.string.greeting_afternoon
    Greeting.EVENING -> R.string.greeting_evening
    Greeting.NIGHT -> R.string.greeting_night
}

/** One day is not a run worth naming. */
private const val MIN_STREAK_TO_SHOW = 2
