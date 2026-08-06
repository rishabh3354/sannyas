package com.sannyas.player.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.sannyas.player.R
import com.sannyas.player.core.common.talkCountText
import com.sannyas.player.domain.repository.CatalogRepository

/**
 * The browsable hierarchy exposed to Android Auto, Assistant and any other
 * `MediaBrowser` client.
 *
 * ```
 * root
 *  ├── categories        → one node per category → its series → its talks
 *  └── all-series        → every series → its talks
 * ```
 *
 * Node ids are namespaced strings so [children] can dispatch on a prefix
 * without keeping any state of its own.
 */
object MediaTree {

    const val ROOT = "sannyas://root"
    private const val CATEGORIES = "sannyas://categories"
    private const val ALL_SERIES = "sannyas://series"
    private const val CATEGORY_PREFIX = "sannyas://category/"
    private const val DISCOURSE_PREFIX = "sannyas://discourse/"

    fun rootItem(context: Context): MediaItem = browsable(
        id = ROOT,
        title = context.getString(R.string.home_title),
        subtitle = context.getString(R.string.media_root_subtitle),
    )

    suspend fun children(
        context: Context,
        repository: CatalogRepository,
        parentId: String,
    ): List<MediaItem> =
        when {
            parentId == ROOT -> listOf(
                categoriesItem(context),
                allSeriesItem(context),
            )

            parentId == CATEGORIES -> repository.getCategories().map { category ->
                browsable(
                    id = CATEGORY_PREFIX + category.name,
                    title = category.name,
                    subtitle = seriesCountText(context, category.discourseCount),
                )
            }

            parentId == ALL_SERIES -> repository.getDiscourses()
                .sortedByDescending { it.popularity }
                .map { discourseItem(context, it) }

            parentId.startsWith(CATEGORY_PREFIX) -> {
                val name = parentId.removePrefix(CATEGORY_PREFIX)
                repository.getDiscourses()
                    .filter { it.category == name }
                    .map { discourseItem(context, it) }
            }

            parentId.startsWith(DISCOURSE_PREFIX) -> {
                val id = parentId.removePrefix(DISCOURSE_PREFIX)
                repository.getDiscourse(id)?.tracks.orEmpty().map(MediaItems::toMediaItem)
            }

            else -> emptyList()
        }

    suspend fun item(
        context: Context,
        repository: CatalogRepository,
        mediaId: String,
    ): MediaItem? = when {
        mediaId == ROOT -> rootItem(context)
        mediaId == CATEGORIES -> categoriesItem(context)
        mediaId == ALL_SERIES -> allSeriesItem(context)

        mediaId.startsWith(CATEGORY_PREFIX) -> {
            val name = mediaId.removePrefix(CATEGORY_PREFIX)
            repository.getCategories().firstOrNull { it.name == name }?.let {
                browsable(mediaId, it.name, seriesCountText(context, it.discourseCount))
            }
        }

        mediaId.startsWith(DISCOURSE_PREFIX) ->
            repository.getDiscourse(mediaId.removePrefix(DISCOURSE_PREFIX))
                ?.let { discourseItem(context, it) }

        else -> repository.getTrack(mediaId)?.let(MediaItems::toMediaItem)
    }

    /**
     * Turns any node id into the list of playable items it stands for, so a
     * client can hand us a series (or a category) and get playback.
     */
    suspend fun resolvePlayable(repository: CatalogRepository, mediaId: String): List<MediaItem> =
        when {
            mediaId.startsWith(DISCOURSE_PREFIX) ->
                repository.getDiscourse(mediaId.removePrefix(DISCOURSE_PREFIX))
                    ?.tracks.orEmpty().map(MediaItems::toMediaItem)

            mediaId.startsWith(CATEGORY_PREFIX) -> {
                val name = mediaId.removePrefix(CATEGORY_PREFIX)
                repository.getDiscourses()
                    .filter { it.category == name }
                    .flatMap { it.tracks }
                    .map(MediaItems::toMediaItem)
            }

            else -> repository.getTrack(mediaId)?.let { listOf(MediaItems.toMediaItem(it)) }
                .orEmpty()
        }

    private fun categoriesItem(context: Context): MediaItem = browsable(
        id = CATEGORIES,
        title = context.getString(R.string.media_categories),
        subtitle = context.getString(R.string.media_categories_subtitle),
    )

    private fun allSeriesItem(context: Context): MediaItem = browsable(
        id = ALL_SERIES,
        title = context.getString(R.string.media_all_series),
        subtitle = context.getString(R.string.media_all_series_subtitle),
    )

    private fun discourseItem(
        context: Context,
        discourse: com.sannyas.player.domain.model.Discourse,
    ): MediaItem = browsable(
        id = DISCOURSE_PREFIX + discourse.id,
        title = discourse.title,
        subtitle = context.getString(
            R.string.dot_separated,
            talkCountText(context, discourse.trackCount),
            discourse.category,
        ),
        artwork = discourse.image,
    )

    private fun seriesCountText(context: Context, count: Int): String =
        context.resources.getQuantityString(R.plurals.series_count, count, count)

    private fun browsable(
        id: String,
        title: String,
        subtitle: String,
        artwork: String? = null,
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setArtist(subtitle)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .apply { artwork?.takeIf { it.isNotBlank() }?.let { setArtworkUri(Uri.parse(it)) } }
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
    }
}
