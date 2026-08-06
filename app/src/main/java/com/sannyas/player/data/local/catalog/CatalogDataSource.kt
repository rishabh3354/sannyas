package com.sannyas.player.data.local.catalog

import android.content.Context
import com.sannyas.player.core.common.DurationFormat
import com.sannyas.player.di.IoDispatcher
import com.sannyas.player.domain.model.Category
import com.sannyas.player.domain.model.Discourse
import com.sannyas.player.domain.model.DiscourseLanguage
import com.sannyas.player.domain.model.Topic
import com.sannyas.player.domain.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and decodes the bundled catalog asset.
 *
 * Parsing happens once on an IO dispatcher; the caller caches the result. The
 * whole file is ~700 KB of JSON, which decodes in well under a frame budget's
 * worth of work off the main thread.
 */
@Singleton
class CatalogDataSource @Inject constructor(
    private val context: Context,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun load(): CatalogSnapshot = withContext(ioDispatcher) {
        val raw = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val dto = json.decodeFromString(CatalogDto.serializer(), raw)
        dto.toSnapshot()
    }

    private fun CatalogDto.toSnapshot(): CatalogSnapshot {
        val discourses = discourses
            .filter { it.tracks.isNotEmpty() }
            .map { it.toDomain() }

        // Prefer the counts declared in the file, but fall back to the real
        // grouping so a hand-edited catalog can omit the category block.
        val countsByName = discourses.groupingBy { it.category }.eachCount()
        val declared = categories.associateBy { it.name }
        val names = (declared.keys + countsByName.keys).distinct()

        val categories = names
            .map { name ->
                Category(
                    id = declared[name]?.id?.takeIf { it.isNotBlank() } ?: name.slugify(),
                    name = name,
                    description = declared[name]?.description.orEmpty(),
                    discourseCount = countsByName[name] ?: 0,
                )
            }
            .filter { it.discourseCount > 0 }
            .sortedByDescending { it.discourseCount }

        // Counts are recomputed from the parsed series so a hand-edited file
        // cannot end up with a topic claiming series it no longer has.
        val topicCounts = discourses.flatMap { it.topics }.groupingBy { it }.eachCount()
        val topics = this.topics
            .filter { (topicCounts[it.id] ?: 0) > 0 }
            .map { Topic(it.id, it.name, it.description, topicCounts[it.id] ?: 0) }

        return CatalogSnapshot(
            discourses = discourses,
            categories = categories,
            topics = topics,
        )
    }

    private fun DiscourseDto.toDomain(): Discourse {
        val discourseId = id?.takeIf { it.isNotBlank() } ?: title.slugify()
        val artwork = image.canonicalArtwork()

        val tracks = tracks.mapIndexed { index, dto ->
            Track(
                id = "$discourseId#${index + 1}",
                discourseId = discourseId,
                title = dto.title,
                url = dto.url,
                durationSeconds = DurationFormat.parseToSeconds(dto.duration),
                index = index + 1,
                number = dto.title.trailingNumber() ?: (index + 1),
                artworkUrl = artwork,
                discourseTitle = title,
                speaker = speaker,
            )
        }

        val declaredTotal = DurationFormat.parseToSeconds(duration)
        val total = if (declaredTotal > 0) declaredTotal else tracks.sumOf { it.durationSeconds }

        return Discourse(
            id = discourseId,
            title = title,
            description = description,
            image = artwork,
            category = category,
            durationSeconds = total,
            speaker = speaker,
            language = DiscourseLanguage.from(language),
            tracks = tracks,
            tags = tags,
            topics = topics,
            popularity = popularity,
        )
    }

    /**
     * Collapses the source's several "no_image" files onto one URL.
     *
     * Despite the name these are not blanks — they are real photographs of
     * Osho, and about 40% of the catalogue uses one. The only problem is that
     * there are two such files holding the same picture, so de-duplicating a
     * rail by URL kept one of each and the row still showed the photo twice.
     * Rewriting them to a single URL lets that de-duplication do its job, while
     * keeping the image itself.
     */
    private fun String.canonicalArtwork(): String =
        if (SHARED_ARTWORK.containsMatchIn(this)) CANONICAL_SHARED_ARTWORK else this

    /**
     * Reads the talk number off the end of a title ("That Art Thou 08" -> 8).
     * Returns null when a title carries no number, so the caller can fall back
     * to positional ordering.
     */
    private fun String.trailingNumber(): Int? =
        TRAILING_NUMBER.find(trim())?.groupValues?.get(1)?.toIntOrNull()

    private fun String.slugify(): String = lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    private companion object {
        const val ASSET_NAME = "discourses.json"
        val TRAILING_NUMBER = Regex("""(\d{1,3})\s*$""")
        val SHARED_ARTWORK = Regex("no[_-]?image", RegexOption.IGNORE_CASE)
        const val CANONICAL_SHARED_ARTWORK =
            "https://oshoworld.com/uploads/pics_1713897522194_no_image.png"
    }
}

/** Immutable, fully-resolved catalog held in memory by the repository. */
data class CatalogSnapshot(
    val discourses: List<Discourse>,
    val categories: List<Category>,
    val topics: List<Topic> = emptyList(),
) {
    val discoursesById: Map<String, Discourse> = discourses.associateBy { it.id }
    val tracksById: Map<String, Track> =
        discourses.flatMap { it.tracks }.associateBy { it.id }

    companion object {
        val Empty = CatalogSnapshot(emptyList(), emptyList(), emptyList())
    }
}
