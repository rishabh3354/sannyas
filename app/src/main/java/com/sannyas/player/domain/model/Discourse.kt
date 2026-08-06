package com.sannyas.player.domain.model

/**
 * A discourse series — the top-level browsable unit in the app.
 *
 * A series groups the individual talks ([tracks]) that were given as one set.
 * Everything here comes from the bundled catalog; nothing is fetched from a
 * backend. Only the audio itself is streamed.
 */
data class Discourse(
    val id: String,
    val title: String,
    val description: String,
    val image: String,
    val category: String,
    /** Total runtime of every track in the series. */
    val durationSeconds: Long,
    val speaker: String,
    val language: DiscourseLanguage,
    val tracks: List<Track>,
    val tags: List<String> = emptyList(),
    /** Life-theme ids this series belongs to. */
    val topics: List<String> = emptyList(),
    /** Editorial ordering hint used for the "Popular" and "Trending" rails. */
    val popularity: Int = 0,
) {
    val trackCount: Int get() = tracks.size

    /** Cheap haystack for the search index; built once per discourse. */
    val searchBlob: String by lazy(LazyThreadSafetyMode.NONE) {
        buildString {
            append(title).append(' ')
            append(category).append(' ')
            append(speaker).append(' ')
            append(description).append(' ')
            tags.forEach { append(it).append(' ') }
            tracks.forEach { append(it.title).append(' ') }
        }.lowercase()
    }
}

/** A single talk within a [Discourse]. */
data class Track(
    val id: String,
    val discourseId: String,
    val title: String,
    val url: String,
    val durationSeconds: Long,
    /** 1-based position within the parent series. Drives queue order. */
    val index: Int,
    /**
     * The talk's own published number, which is what listeners recognise.
     *
     * This is not always [index]: the source's numbering has gaps in 14 of the
     * 173 series (there is no "That Art Thou 04", for example), so the fourth
     * item in the list is talk 05. Showing [index] there would contradict the
     * title sitting next to it.
     */
    val number: Int,
    val artworkUrl: String,
    val discourseTitle: String,
    val speaker: String,
) {
    val durationMs: Long get() = durationSeconds * 1000L
}

/** The language a series was spoken in. */
enum class DiscourseLanguage {
    ENGLISH,
    HINDI,
    ;

    companion object {
        fun from(raw: String?): DiscourseLanguage =
            if (raw?.equals("hindi", ignoreCase = true) == true) HINDI else ENGLISH
    }
}

/**
 * Which languages the listener wants to browse.
 *
 * Stored as a preference rather than derived from the device locale: plenty of
 * listeners want the Hindi discourses on an English phone, and vice versa.
 */
enum class ContentLanguage {
    ALL,
    ENGLISH,
    HINDI,
    ;

    fun accepts(language: DiscourseLanguage): Boolean = when (this) {
        ALL -> true
        ENGLISH -> language == DiscourseLanguage.ENGLISH
        HINDI -> language == DiscourseLanguage.HINDI
    }
}

/**
 * A life theme — "Fear & Anxiety", "Death & Dying".
 *
 * Categories organise the library the way a scholar would, by master and
 * tradition. Topics organise it the way someone actually arrives: with a
 * problem. Both are needed; this is the one a newcomer can use.
 */
data class Topic(
    val id: String,
    val name: String,
    val description: String,
    val seriesCount: Int,
)

/**
 * A continuous, never-ending stream assembled from the catalogue.
 *
 * Not an external radio feed: a station is just a filter over the talks already
 * in the app, played in shuffled order and topped up as it runs down. That
 * makes it work offline-ish, needs no third-party rights, and suits a library
 * of 5,500 talks that a listener could never work through deliberately.
 */
data class Station(
    val id: String,
    /**
     * The category this station is named after. Blank for the whole-library
     * station, whose name is a translated resource rather than catalogue data.
     */
    val name: String,
    /** Null means "draw from the whole library". */
    val category: String?,
    val trackCount: Int,
    val seriesCount: Int,
) {
    val isWholeLibrary: Boolean get() = category == null
}

/** A browsable grouping shown on the home screen and search filters. */
data class Category(
    val id: String,
    val name: String,
    val description: String,
    val discourseCount: Int,
)
