package com.sannyas.player.data.local.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format of `assets/discourses.json`.
 *
 * Every field the app does not strictly need has a default, so the bundled
 * catalog can be replaced with a richer or leaner file without touching code.
 * `ignoreUnknownKeys` is enabled on the parser for the same reason.
 */
@Serializable
data class CatalogDto(
    val version: Int = 1,
    val source: String = "",
    val categories: List<CategoryDto> = emptyList(),
    val topics: List<TopicDto> = emptyList(),
    val discourses: List<DiscourseDto> = emptyList(),
)

@Serializable
data class CategoryDto(
    val id: String = "",
    val name: String,
    val description: String = "",
    @SerialName("discourseCount") val discourseCount: Int = 0,
)

@Serializable
data class TopicDto(
    val id: String,
    val name: String,
    val description: String = "",
    val seriesCount: Int = 0,
)

@Serializable
data class DiscourseDto(
    /** Optional: falls back to a slug derived from [title]. */
    val id: String? = null,
    val title: String,
    val description: String = "",
    val image: String = "",
    val category: String = "Discourses",
    /** `HH:MM:SS`. Recomputed from the tracks when absent or zero. */
    val duration: String = "",
    val speaker: String = "Osho",
    /** "english" or "hindi"; anything unrecognised falls back to English. */
    val language: String = "english",
    val tags: List<String> = emptyList(),
    /** Life themes this series is filed under; see tools/build_topics.py. */
    val topics: List<String> = emptyList(),
    val popularity: Int = 0,
    val tracks: List<TrackDto> = emptyList(),
)

@Serializable
data class TrackDto(
    val title: String,
    val url: String,
    /** `HH:MM:SS` or `MM:SS`. */
    val duration: String = "",
)
