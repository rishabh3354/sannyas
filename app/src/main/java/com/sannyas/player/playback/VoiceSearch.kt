package com.sannyas.player.playback

import com.sannyas.player.di.ApplicationScope
import com.sannyas.player.domain.model.Track
import com.sannyas.player.domain.repository.CatalogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a spoken query into something playing.
 *
 * Serves "play Osho on <assistant>", the Android Auto search box, and a
 * headset button held down — all of which arrive as a plain string with no
 * indication of whether it names a series, a talk or a tradition.
 *
 * Resolution is deliberately ordered by what people actually say. Series names
 * come first because that is how these discourses are known ("play The Book of
 * Secrets"); an exact talk is next; and a bare "play something" still has to
 * result in audio rather than an apology, so it falls through to the catalogue
 * at large.
 */
@Singleton
class VoiceSearch @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val playerConnection: PlayerConnection,
    @ApplicationScope private val scope: CoroutineScope,
) {

    fun play(query: String?) {
        scope.launch {
            val queue = resolve(query)
            if (queue.isNotEmpty()) playerConnection.play(queue, startIndex = 0)
        }
    }

    /** Exposed separately so the media session can answer a search without playing. */
    suspend fun resolve(query: String?): List<Track> {
        val needle = query?.trim().orEmpty()

        if (needle.isEmpty()) {
            // "Play some Osho." Anything is a better answer than nothing.
            return catalogRepository.getDiscourses()
                .sortedByDescending { it.popularity }
                .firstOrNull()
                ?.tracks
                .orEmpty()
        }

        val results = catalogRepository.search(needle)

        results.discourses.firstOrNull()?.let { return it.tracks }
        if (results.tracks.isNotEmpty()) return results.tracks

        // A tradition rather than a title — "play some Zen".
        results.categories.firstOrNull()?.let { category ->
            return catalogRepository.getDiscourses()
                .filter { it.category.equals(category.name, ignoreCase = true) }
                .sortedByDescending { it.popularity }
                .flatMap { it.tracks }
                .take(MAX_CATEGORY_TRACKS)
        }

        return emptyList()
    }

    private companion object {
        /** Enough to keep playing for hours without handing over a 500-item queue. */
        const val MAX_CATEGORY_TRACKS = 60
    }
}
