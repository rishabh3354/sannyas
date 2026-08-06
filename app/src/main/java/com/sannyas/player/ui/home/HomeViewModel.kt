package com.sannyas.player.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sannyas.player.data.local.datastore.SettingsDataStore
import com.sannyas.player.domain.model.Category
import com.sannyas.player.domain.model.ContentLanguage
import com.sannyas.player.domain.model.Discourse
import com.sannyas.player.domain.model.PlaybackProgress
import com.sannyas.player.domain.model.Topic
import com.sannyas.player.domain.model.Track
import com.sannyas.player.domain.repository.CatalogRepository
import com.sannyas.player.domain.repository.UserDataRepository
import com.sannyas.player.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.sannyas.player.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.sannyas.player.core.common.DailyRhythm
import java.time.LocalDate
import java.util.Calendar
import javax.inject.Inject
import kotlin.random.Random

/** Bundles the non-catalogue inputs so the main `combine` stays within arity. */
private data class HomeSideData(
    val error: Boolean,
    val language: ContentLanguage,
    val topics: List<Topic>,
    val listenedAt: List<Long>,
)

/** The one talk everybody on this language setting is offered today. */
data class TalkOfTheDay(val track: Track, val discourse: Discourse)

/** Time of day, resolved to wording by the UI so it can be localised. */
enum class Greeting { MORNING, AFTERNOON, EVENING, NIGHT }

/** A resumable item, already joined to its catalog metadata for rendering. */
data class ContinueItem(
    val track: Track,
    val discourse: Discourse,
    val positionMs: Long,
    val durationMs: Long,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    /** True when the catalogue could not be read; the wording is a resource. */
    val hasError: Boolean = false,
    val greeting: Greeting = Greeting.MORNING,
    val talkOfTheDay: TalkOfTheDay? = null,
    /** Consecutive days with any listening; 0 or 1 is not worth mentioning. */
    val streakDays: Int = 0,
    val continueListening: List<ContinueItem> = emptyList(),
    val recentlyAdded: List<Discourse> = emptyList(),
    val popular: List<Discourse> = emptyList(),
    val meditation: List<Discourse> = emptyList(),
    val categories: List<Category> = emptyList(),
    val contentLanguage: ContentLanguage = ContentLanguage.ALL,
    val topics: List<Topic> = emptyList(),
    val trending: List<Discourse> = emptyList(),
    val recommended: List<Discourse> = emptyList(),
) {
    val isEmpty: Boolean
        get() = !isLoading && recentlyAdded.isEmpty() && popular.isEmpty()
}

/**
 * Builds the home feed.
 *
 * The catalog has no editorial rails of its own, so each row is derived: some
 * from real signal (progress, favourites), some from stable deterministic
 * ordering. Determinism matters — a feed that reshuffles on every recomposition
 * feels broken, so the pseudo-random rails are seeded by the day.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val userDataRepository: UserDataRepository,
    private val settings: SettingsDataStore,
    private val playerConnection: PlayerConnection,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = refreshing.asStateFlow()

    private val loadError = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        catalogRepository.observeDiscourses(),
        userDataRepository.observeProgress(),
        userDataRepository.observeFavouriteDiscourseIds(),
        combine(
            loadError,
            settings.settings,
            catalogRepository.observeTopics(),
            userDataRepository.observeHistory(HISTORY_FOR_STREAK),
        ) { error, prefs, topics, history ->
            HomeSideData(error, prefs.contentLanguage, topics, history.map { it.playedAt })
        },
    ) { discourses, progress, favourites, side ->
        val (error, language, topics, listenedAt) = side
        if (discourses.isEmpty()) {
            HomeUiState(
                isLoading = !error,
                hasError = error,
                greeting = greeting(),
                contentLanguage = language,
            )
        } else {
            buildState(discourses, progress, favourites)
                .copy(
                    contentLanguage = language,
                    topics = topics,
                    talkOfTheDay = talkOfTheDay(discourses),
                    streakDays = DailyRhythm.streakDays(listenedAt, LocalDate.now()),
                )
        }
    }.flowOn(defaultDispatcher).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(greeting = greeting()),
    )

    init {
        load()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            runCatching { catalogRepository.refresh() }
                .onFailure { loadError.value = true }
                .onSuccess { loadError.value = false }
            refreshing.value = false
        }
    }

    fun retry() = load()

    fun setContentLanguage(language: ContentLanguage) {
        viewModelScope.launch { settings.setContentLanguage(language) }
    }

    /** Starts a series from its first talk. */
    fun playDiscourse(discourse: Discourse) {
        playerConnection.play(discourse.tracks, startIndex = 0)
    }

    /** Resumes a partly-heard talk within its own series queue. */
    fun resume(item: ContinueItem) {
        val index = item.discourse.tracks.indexOfFirst { it.id == item.track.id }
        playerConnection.play(
            queue = item.discourse.tracks,
            startIndex = index.coerceAtLeast(0),
            startPositionMs = item.positionMs,
        )
    }

    private fun load() {
        viewModelScope.launch {
            runCatching { catalogRepository.refresh() }
                .onFailure { loadError.value = true }
                .onSuccess { loadError.value = false }
        }
    }

    private fun buildState(
        discourses: List<Discourse>,
        progress: List<PlaybackProgress>,
        favourites: Set<String>,
    ): HomeUiState {
        val byId = discourses.associateBy { it.id }
        val tracksById = discourses.flatMap { it.tracks }.associateBy { it.id }

        val continueItems = progress
            .filter { it.isMeaningful }
            .sortedByDescending { it.updatedAt }
            .mapNotNull { entry ->
                val track = tracksById[entry.trackId] ?: return@mapNotNull null
                val discourse = byId[entry.discourseId] ?: return@mapNotNull null
                ContinueItem(track, discourse, entry.positionMs, entry.durationMs)
            }
            .take(10)

        // Every rail is de-duplicated by cover *before* being trimmed, so it
        // still fills up with twelve visually distinct cards.
        val popular = discourses.sortedByDescending { it.popularity }
            .distinctByArtwork()
            .take(12)

        // "Recently added" has no timestamp in the catalog, so it reads the tail
        // of the file — the natural place new entries are appended.
        val recentlyAdded = discourses.asReversed().distinctByArtwork().take(12)

        val meditation = discourses
            .filter { discourse ->
                discourse.category.contains("Meditation", ignoreCase = true) ||
                    discourse.tags.any { it.equals("meditation", ignoreCase = true) } ||
                    discourse.category.contains("Yoga", ignoreCase = true) ||
                    discourse.category.contains("Tantra", ignoreCase = true)
            }
            .sortedByDescending { it.popularity }
            .distinctByArtwork()
            .take(12)

        // Seeded by the day so the rail is stable while the app is open but
        // still feels fresh tomorrow.
        val daySeed = Calendar.getInstance().get(Calendar.DAY_OF_YEAR).toLong()
        val trending = discourses.shuffled(Random(daySeed)).distinctByArtwork().take(12)

        // Favourites steer recommendations towards categories the listener has
        // already chosen; with no favourites yet it falls back to shorter,
        // more approachable series.
        val favouredCategories = favourites.mapNotNull { byId[it]?.category }.toSet()
        val recommended = if (favouredCategories.isEmpty()) {
            discourses.filter { it.trackCount in 5..15 }
                .shuffled(Random(daySeed + 1))
                .distinctByArtwork()
                .take(12)
        } else {
            discourses
                .filter { it.category in favouredCategories && it.id !in favourites }
                .sortedByDescending { it.popularity }
                .distinctByArtwork()
                .take(12)
                .ifEmpty { popular }
        }

        return HomeUiState(
            isLoading = false,
            hasError = false,
            greeting = greeting(),
            continueListening = continueItems,
            recentlyAdded = recentlyAdded,
            popular = popular,
            meditation = meditation,
            categories = discourses.groupingBy { it.category }.eachCount()
                .map { (name, count) -> Category(name.lowercase(), name, "", count) }
                .sortedByDescending { it.discourseCount },
            trending = trending,
            recommended = recommended,
        )
    }

    /**
     * Drops repeats of the same cover within a single rail.
     *
     * A large share of the catalogue ships with one generic cover from the
     * source, so an unfiltered rail can show four identical-looking cards and
     * read as a rendering fault rather than as content. This is presentational
     * only — the ranking is untouched, and nothing is hidden from the "See all"
     * lists or from search.
     *
     * Series with no artwork are deliberately kept: those fall back to a
     * gradient derived from the series id, which is already distinct per series.
     */
    private fun List<Discourse>.distinctByArtwork(): List<Discourse> {
        val seen = mutableSetOf<String>()
        return filter { it.image.isBlank() || seen.add(it.image) }
    }

    /**
     * Today's talk.
     *
     * Drawn from the already language-filtered catalogue, so two listeners on
     * the same setting get the same talk — the shared part of "everyone gets
     * the same one" that still leaves it in a language they follow.
     */
    private fun talkOfTheDay(discourses: List<Discourse>): TalkOfTheDay? {
        val byId = discourses.associateBy { it.id }
        val tracks = discourses.flatMap { it.tracks }
        val index = DailyRhythm.indexForDay(LocalDate.now().toEpochDay(), tracks.size)
        val track = tracks.getOrNull(index) ?: return null
        val discourse = byId[track.discourseId] ?: return null
        return TalkOfTheDay(track, discourse)
    }

    /** Starts today's talk inside its own series, so it can run on afterwards. */
    fun playTalkOfTheDay(item: TalkOfTheDay) {
        val queue = item.discourse.tracks
        playerConnection.play(
            queue = queue,
            startIndex = queue.indexOfFirst { it.id == item.track.id }.coerceAtLeast(0),
        )
    }

    private fun greeting(): Greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> Greeting.MORNING
        in 12..16 -> Greeting.AFTERNOON
        in 17..21 -> Greeting.EVENING
        else -> Greeting.NIGHT
    }

    private companion object {
        /** Two months of history is far more than any believable streak needs. */
        const val HISTORY_FOR_STREAK = 400
    }
}
