package com.sannyas.player.ui.library

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sannyas.player.R
import com.sannyas.player.domain.model.Discourse
import com.sannyas.player.domain.model.DiscourseLanguage
import com.sannyas.player.domain.model.Topic
import com.sannyas.player.domain.model.Track
import com.sannyas.player.domain.repository.CatalogRepository
import com.sannyas.player.domain.repository.UserDataRepository
import com.sannyas.player.playback.PlayerConnection
import com.sannyas.player.ui.home.ContinueItem
import com.sannyas.player.ui.navigation.CollectionKind
import com.sannyas.player.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import com.sannyas.player.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import kotlin.random.Random

/**
 * What a collection screen is showing.
 *
 * A collection is either a grid of series or a list of talks, never both, so
 * the state carries whichever list applies and the screen renders accordingly.
 */
data class CollectionUiState(
    /** Set when the heading is app wording; null when it names catalogue data. */
    @StringRes val titleRes: Int? = null,
    /** A category or theme name, which comes from the catalogue and is not translated. */
    val titleText: String? = null,
    @StringRes val subtitleRes: Int? = null,
    val subtitleText: String? = null,
    /** Argument for [subtitleRes] when the wording carries a count. */
    val subtitleCount: Int? = null,
    /** Null for a category or theme; drives the empty-state copy. */
    val kind: CollectionKind? = null,
    val isLoading: Boolean = true,
    val discourses: List<Discourse> = emptyList(),
    /** Set instead of [discourses] when the screen asks which language first. */
    val languageChoices: List<LanguageChoice> = emptyList(),
    val tracks: List<TrackListItem> = emptyList(),
    val continueItems: List<ContinueItem> = emptyList(),
    val currentTrackId: String? = null,
    val isPlaying: Boolean = false,
) {
    val isEmpty: Boolean
        get() = !isLoading && discourses.isEmpty() && languageChoices.isEmpty() &&
            tracks.isEmpty() && continueItems.isEmpty()
}

data class TrackListItem(val track: Track, val discourse: Discourse)

/**
 * A language to browse, offered as a tile.
 *
 * "All Series" is 351 sets in two languages a listener rarely both follows.
 * Asking which one first is a shorter path than scrolling past half the
 * catalogue to reach the other half.
 */
data class LanguageChoice(
    @StringRes val titleRes: Int,
    val count: Int,
    val kind: CollectionKind,
)

/**
 * Backs both the category route and every "See all" destination.
 *
 * One ViewModel rather than eight near-identical ones: the route argument
 * selects a projection over the same catalog and user data, which keeps the
 * ranking rules for Popular and Trending in exactly one place.
 */
@HiltViewModel
class CollectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalogRepository: CatalogRepository,
    private val userDataRepository: UserDataRepository,
    private val playerConnection: PlayerConnection,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val categoryName: String? = savedStateHandle[Routes.CATEGORY_ARG]
    private val topicId: String? = savedStateHandle[Routes.TOPIC_ARG]
    private val topic = MutableStateFlow<Topic?>(null)
    private val collectionKind: CollectionKind? = savedStateHandle
        .get<String>(Routes.COLLECTION_ARG)
        ?.let { raw -> runCatching { CollectionKind.valueOf(raw) }.getOrNull() }

    private val loading = MutableStateFlow(true)

    /**
     * Ordering snapshot for the history-style collections.
     *
     * History is sorted by "last played", so tapping a row re-records it and the
     * row you just touched jumps to the top under your finger. Freezing the
     * order for as long as the screen is open keeps the list still; it is
     * rebuilt fresh the next time the screen is opened.
     */
    private var frozenOrder: List<String>? = null

    private fun <T> stabilise(items: List<T>, idOf: (T) -> String): List<T> {
        val order = frozenOrder
        if (order == null) {
            frozenOrder = items.map(idOf)
            return items
        }
        val rank = order.withIndex().associate { (i, id) -> id to i }
        // Anything played since the screen opened is unranked; keep it at the
        // end rather than letting it displace what the user is looking at.
        return items.sortedBy { rank[idOf(it)] ?: Int.MAX_VALUE }
    }

    val uiState: StateFlow<CollectionUiState> = combine(
        catalogRepository.observeDiscourses(),
        userDataRepository.observeFavouriteDiscourseIds(),
        userDataRepository.observeProgress(),
        combine(
            userDataRepository.observeHistory(),
            userDataRepository.observeFavouriteTrackIds(),
        ) { history, favouriteTracks -> history to favouriteTracks },
        combine(
            loading,
            playerConnection.state,
            topic,
            catalogRepository.observeAllDiscourses(),
        ) { isLoading, player, _, all -> Triple(isLoading, player, all) },
    ) { discourses, favourites, progress, (history, favouriteTrackIds), (isLoading, player, allDiscourses) ->
        val byId = discourses.associateBy { it.id }
        val tracksById = discourses.flatMap { it.tracks }.associateBy { it.id }

        val base = CollectionUiState(
            isLoading = isLoading && discourses.isEmpty(),
            currentTrackId = player.currentTrack?.id,
            isPlaying = player.isPlaying,
        )

        when {
            topicId != null -> base.copy(
                titleText = topic.value?.name,
                titleRes = R.string.collection_theme.takeIf { topic.value == null },
                subtitleText = topic.value?.description.orEmpty(),
                discourses = discourses
                    .filter { topicId in it.topics }
                    .sortedByDescending { it.popularity },
            )

            categoryName != null -> base.copy(
                titleText = categoryName,
                subtitleRes = R.string.collection_category_subtitle,
                discourses = discourses.filter { it.category == categoryName }
                    .sortedByDescending { it.popularity },
            )

            // Series and single talks in one place. They are the same gesture
            // to a listener, and two shelves for one idea read as a mistake.
            collectionKind == CollectionKind.FAVOURITES -> base.copy(
                kind = CollectionKind.FAVOURITES,
                titleRes = CollectionKind.FAVOURITES.titleRes,
                subtitleRes = R.string.library_favourites_subtitle,
                discourses = favourites.mapNotNull { byId[it] },
                tracks = favouriteTrackIds.mapNotNull { id ->
                    val track = tracksById[id] ?: return@mapNotNull null
                    val discourse = byId[track.discourseId] ?: return@mapNotNull null
                    TrackListItem(track, discourse)
                },
            )

            collectionKind == CollectionKind.CONTINUE_LISTENING -> base.copy(
                kind = CollectionKind.CONTINUE_LISTENING,
                titleRes = CollectionKind.CONTINUE_LISTENING.titleRes,
                subtitleRes = R.string.collection_continue_subtitle,
                // Frozen for as long as the screen is open. Resuming a talk
                // rewrites its position, which would otherwise fling the row
                // straight to the top from under the finger that tapped it.
                continueItems = stabilise(
                    progress
                        .filter { it.isMeaningful }
                        .sortedByDescending { it.updatedAt }
                        .mapNotNull { entry ->
                            val track = tracksById[entry.trackId] ?: return@mapNotNull null
                            val discourse = byId[entry.discourseId] ?: return@mapNotNull null
                            ContinueItem(track, discourse, entry.positionMs, entry.durationMs)
                        },
                ) { it.track.id },
            )

            collectionKind == CollectionKind.HISTORY -> base.copy(
                kind = collectionKind,
                titleRes = collectionKind.titleRes,
                subtitleRes = R.string.collection_history_subtitle,
                tracks = stabilise(
                    history.mapNotNull { entry ->
                        val track = tracksById[entry.trackId] ?: return@mapNotNull null
                        val discourse = byId[entry.discourseId] ?: return@mapNotNull null
                        TrackListItem(track, discourse)
                    },
                ) { it.track.id },
            )

            collectionKind == CollectionKind.ALL_SERIES -> base.copy(
                kind = CollectionKind.ALL_SERIES,
                titleRes = CollectionKind.ALL_SERIES.titleRes,
                subtitleRes = R.string.collection_all_subtitle,
                subtitleCount = allDiscourses.size,
                languageChoices = listOf(
                    DiscourseLanguage.ENGLISH to CollectionKind.ALL_SERIES_ENGLISH,
                    DiscourseLanguage.HINDI to CollectionKind.ALL_SERIES_HINDI,
                ).mapNotNull { (language, kind) ->
                    val count = allDiscourses.count { it.language == language }
                    if (count == 0) null else LanguageChoice(kind.titleRes, count, kind)
                },
            )

            // Both ignore the discourse-language filter on purpose: the
            // listener has just said which language they want.
            collectionKind == CollectionKind.ALL_SERIES_ENGLISH ||
                collectionKind == CollectionKind.ALL_SERIES_HINDI -> {
                val language = if (collectionKind == CollectionKind.ALL_SERIES_ENGLISH) {
                    DiscourseLanguage.ENGLISH
                } else {
                    DiscourseLanguage.HINDI
                }
                val matching = allDiscourses
                    .filter { it.language == language }
                    .sortedBy { it.title.lowercase() }
                base.copy(
                    kind = collectionKind,
                    titleRes = collectionKind.titleRes,
                    subtitleRes = R.string.collection_all_subtitle,
                    subtitleCount = matching.size,
                    discourses = matching,
                )
            }

            else -> {
                val kind = collectionKind ?: CollectionKind.ALL_SERIES
                base.copy(
                    kind = kind,
                    titleRes = kind.titleRes,
                    subtitleRes = R.string.collection_all_subtitle,
                    subtitleCount = discourses.size,
                    discourses = discourses.project(kind, favourites),
                )
            }
        }
    }.flowOn(defaultDispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionUiState())

    init {
        viewModelScope.launch {
            topicId?.let { topic.value = catalogRepository.getTopic(it) }
            catalogRepository.getDiscourses()
            loading.value = false
        }
    }

    /**
     * Applies the same ordering rules the home rails use, so "See all" is a
     * genuine continuation of the row the user tapped rather than a new sort.
     */
    private fun List<Discourse>.project(
        kind: CollectionKind,
        favourites: Set<String>,
    ): List<Discourse> {
        val daySeed = Calendar.getInstance().get(Calendar.DAY_OF_YEAR).toLong()
        return when (kind) {
            CollectionKind.POPULAR -> sortedByDescending { it.popularity }
            CollectionKind.RECENTLY_ADDED -> reversed()
            CollectionKind.TRENDING -> shuffled(Random(daySeed))
            CollectionKind.MEDITATION -> filter { discourse ->
                discourse.category.contains("Meditation", ignoreCase = true) ||
                    discourse.category.contains("Yoga", ignoreCase = true) ||
                    discourse.category.contains("Tantra", ignoreCase = true) ||
                    discourse.tags.any { it.equals("meditation", ignoreCase = true) }
            }.sortedByDescending { it.popularity }

            CollectionKind.RECOMMENDED -> {
                val favoured = favourites.mapNotNull { id -> firstOrNull { it.id == id }?.category }
                    .toSet()
                if (favoured.isEmpty()) {
                    filter { it.trackCount in 5..15 }.shuffled(Random(daySeed + 1))
                } else {
                    filter { it.category in favoured && it.id !in favourites }
                        .sortedByDescending { it.popularity }
                }
            }

            else -> sortedBy { it.title.lowercase() }
        }
    }

    /**
     * Plays from the list on screen, queueing the rest of *that* list.
     *
     * Tapping a favourite talk used to hand the player its parent series, so
     * "up next" filled with fifty discourses the listener had not chosen. What
     * you are looking at is what should play on.
     */
    fun playTrack(item: TrackListItem) {
        val queue = uiState.value.tracks.map { it.track }.ifEmpty { item.discourse.tracks }
        playerConnection.play(
            queue = queue,
            startIndex = queue.indexOfFirst { it.id == item.track.id }.coerceAtLeast(0),
        )
    }

    fun resume(item: ContinueItem) {
        val index = item.discourse.tracks.indexOfFirst { it.id == item.track.id }
        playerConnection.play(
            queue = item.discourse.tracks,
            startIndex = index.coerceAtLeast(0),
            startPositionMs = item.positionMs,
        )
    }

    /** Drops one talk from Continue Listening, leaving the rest of the list still. */
    fun removeContinueItem(item: ContinueItem) {
        viewModelScope.launch { userDataRepository.clearProgress(item.track.id) }
    }

    fun clearAllContinue() {
        viewModelScope.launch { userDataRepository.clearAllProgress() }
    }

}
