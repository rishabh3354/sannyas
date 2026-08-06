package com.sannyas.player.data.repository

import com.sannyas.player.data.local.catalog.CatalogDataSource
import com.sannyas.player.data.local.catalog.CatalogSnapshot
import com.sannyas.player.data.local.datastore.SettingsDataStore
import com.sannyas.player.domain.model.Category
import com.sannyas.player.domain.model.Discourse
import com.sannyas.player.domain.model.Station
import com.sannyas.player.domain.model.Topic
import com.sannyas.player.domain.model.Track
import com.sannyas.player.domain.repository.CatalogRepository
import com.sannyas.player.domain.repository.SearchResults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory catalog backed by the bundled JSON asset.
 *
 * The snapshot is loaded lazily on first access and then reused; [refresh]
 * re-reads it, which is what pull-to-refresh calls. A [Mutex] makes concurrent
 * first-access from several screens load the file exactly once.
 */
@Singleton
class CatalogRepositoryImpl @Inject constructor(
    private val dataSource: CatalogDataSource,
    private val settings: SettingsDataStore,
) : CatalogRepository {

    private val snapshot = MutableStateFlow<CatalogSnapshot?>(null)
    private val loadMutex = Mutex()

    /**
     * Everything browsable is filtered by the listener's language preference.
     *
     * Lookups by id ([getDiscourse], [getTrack]) deliberately are *not*: a
     * downloaded talk, a resume position or a queue entry must keep resolving
     * even after its language has been filtered out of the browse experience.
     */
    override fun observeDiscourses(): Flow<List<Discourse>> =
        combine(
            snapshot.asStateFlow().filterNotNull(),
            settings.settings.map { it.contentLanguage }.distinctUntilChanged(),
        ) { current, language ->
            current.discourses.filter { language.accepts(it.language) }
        }

    override fun observeAllDiscourses(): Flow<List<Discourse>> =
        snapshot.asStateFlow().filterNotNull().map { it.discourses }

    override suspend fun getDiscourses(): List<Discourse> {
        val language = settings.settings.first().contentLanguage
        return snapshot().discourses.filter { language.accepts(it.language) }
    }

    override suspend fun getDiscourse(id: String): Discourse? = snapshot().discoursesById[id]

    override suspend fun getTrack(trackId: String): Track? = snapshot().tracksById[trackId]

    override suspend fun getCategories(): List<Category> {
        val language = settings.settings.first().contentLanguage
        // Recount rather than reuse the file's totals, which cover both languages.
        return snapshot().discourses
            .filter { language.accepts(it.language) }
            .groupingBy { it.category }
            .eachCount()
            .map { (name, count) -> Category(name.lowercase(), name, "", count) }
            .sortedByDescending { it.discourseCount }
    }

    override fun observeCategories(): Flow<List<Category>> =
        observeDiscourses().map { discourses ->
            discourses.groupingBy { it.category }
                .eachCount()
                .map { (name, count) -> Category(name.lowercase(), name, "", count) }
                .sortedByDescending { it.discourseCount }
        }

    /**
     * Topic counts are re-derived from the visible series, so switching
     * language updates the numbers rather than leaving the file's totals —
     * which span both languages — on screen.
     */
    override fun observeTopics(): Flow<List<Topic>> =
        combine(
            snapshot.asStateFlow().filterNotNull(),
            observeDiscourses(),
        ) { current, visible ->
            val counts = visible.flatMap { it.topics }.groupingBy { it }.eachCount()
            current.topics
                .filter { (counts[it.id] ?: 0) > 0 }
                .map { it.copy(seriesCount = counts.getValue(it.id)) }
        }

    override suspend fun getTopic(topicId: String): Topic? =
        snapshot().topics.firstOrNull { it.id == topicId }

    override fun observeStations(): Flow<List<Station>> =
        observeDiscourses().map { discourses -> discourses.toStations() }

    override suspend fun stationTracks(stationId: String, limit: Int): List<Track> {
        val language = settings.settings.first().contentLanguage
        val pool = snapshot().discourses.filter { language.accepts(it.language) }
        val station = pool.toStations().firstOrNull { it.id == stationId } ?: return emptyList()

        return pool
            .filter { station.category == null || it.category == station.category }
            .flatMap { it.tracks }
            .shuffled()
            .take(limit)
    }

    /**
     * Derives the station list.
     *
     * "Osho Radio" always draws from everything; the rest are the categories
     * broad enough to sustain a stream on their own. A category with only a
     * couple of series would loop audibly, so [MIN_STATION_SERIES] keeps those
     * out rather than offering a station that repeats itself.
     */
    private fun List<Discourse>.toStations(): List<Station> {
        if (isEmpty()) return emptyList()

        val everything = Station(
            id = ALL_STATION_ID,
            name = "",
            category = null,
            trackCount = sumOf { it.trackCount },
            seriesCount = size,
        )

        val byCategory = groupBy { it.category }
            .filter { (_, series) -> series.size >= MIN_STATION_SERIES }
            .map { (category, series) ->
                Station(
                    id = "station:$category",
                    name = category,
                    category = category,
                    trackCount = series.sumOf { it.trackCount },
                    seriesCount = series.size,
                )
            }
            .sortedByDescending { it.trackCount }

        return listOf(everything) + byCategory
    }

    override suspend fun refresh() {
        loadMutex.withLock { snapshot.value = dataSource.load() }
    }

    override suspend fun search(query: String): SearchResults {
        val needle = query.trim().lowercase()
        if (needle.length < MIN_QUERY_LENGTH) return SearchResults()

        // Search deliberately spans both languages: a listener who knows a
        // Hindi title by name should find it even while browsing English. The
        // language preference shapes what is *browsable*, not what is findable.
        val current = snapshot()
        val terms = needle.split(' ').filter { it.isNotBlank() }

        val discourses = current.discourses
            .mapNotNull { discourse ->
                val score = discourse.score(needle, terms) ?: return@mapNotNull null
                discourse to score
            }
            .sortedWith(compareByDescending<Pair<Discourse, Int>> { it.second }
                .thenByDescending { it.first.popularity })
            .map { it.first }
            .take(MAX_DISCOURSE_RESULTS)

        val tracks = current.discourses
            .asSequence()
            .flatMap { it.tracks.asSequence() }
            .filter { track -> terms.all { track.title.lowercase().contains(it) } }
            .take(MAX_TRACK_RESULTS)
            .toList()

        val categories = current.categories
            .filter { it.name.lowercase().contains(needle) }

        return SearchResults(discourses = discourses, tracks = tracks, categories = categories)
    }

    override suspend fun suggestions(query: String, limit: Int): List<String> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        val current = snapshot()

        // Titles first — they are what a user is most likely reaching for —
        // then categories, then tags.
        val titles = current.discourses
            .asSequence()
            .filter { it.title.lowercase().contains(needle) }
            .sortedWith(
                compareBy<Discourse> { !it.title.lowercase().startsWith(needle) }
                    .thenByDescending { it.popularity },
            )
            .map { it.title }

        val categories = current.categories
            .asSequence()
            .filter { it.name.lowercase().contains(needle) }
            .map { it.name }

        val tags = current.discourses
            .asSequence()
            .flatMap { it.tags.asSequence() }
            .filter { it.lowercase().contains(needle) }
            .map { tag -> tag.replaceFirstChar { it.uppercase() } }

        return (titles + categories + tags).distinct().take(limit).toList()
    }

    /**
     * Ranks a discourse against the query. Returns null when it does not match
     * every term, so multi-word queries narrow rather than widen.
     */
    private fun Discourse.score(needle: String, terms: List<String>): Int? {
        val title = title.lowercase()
        if (!terms.all { searchBlob.contains(it) }) return null

        var score = 0
        if (title == needle) score += 1000
        if (title.startsWith(needle)) score += 400
        if (title.contains(needle)) score += 250
        if (category.lowercase().contains(needle)) score += 120
        if (tags.any { it.lowercase() == needle }) score += 100
        if (terms.all { title.contains(it) }) score += 60
        if (description.lowercase().contains(needle)) score += 20
        return score
    }

    private suspend fun snapshot(): CatalogSnapshot {
        snapshot.value?.let { return it }
        return loadMutex.withLock {
            snapshot.value ?: dataSource.load().also { snapshot.value = it }
        }
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
        const val MAX_DISCOURSE_RESULTS = 40
        const val MAX_TRACK_RESULTS = 30
        const val MIN_STATION_SERIES = 4
        const val ALL_STATION_ID = "station:all"
    }
}
