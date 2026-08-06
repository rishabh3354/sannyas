package com.osho.player.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.osho.player.R

/**
 * Every route in the app.
 *
 * Routes are plain strings rather than type-safe navigation entries because the
 * graph is small and flat; the helper builders below keep argument encoding in
 * one place, which is where the real risk of a typo lives.
 */
object Routes {
    const val HOME = "home"
    const val RADIO = "radio"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"

    const val DISCOURSE_ARG = "discourseId"
    const val DISCOURSE = "discourse/{$DISCOURSE_ARG}"

    const val CATEGORY_ARG = "categoryName"
    const val CATEGORY = "category/{$CATEGORY_ARG}"

    const val TOPIC_ARG = "topicId"
    const val TOPIC = "topic/{$TOPIC_ARG}"

    const val COLLECTION_ARG = "collection"
    const val COLLECTION = "collection/{$COLLECTION_ARG}"

    const val PLAYLIST_ARG = "playlistId"
    const val PLAYLIST = "playlist/{$PLAYLIST_ARG}"

    const val PLAYLISTS = "playlists"

    const val BROWSE_ARG = "browse"
    const val BROWSE = "browse/{$BROWSE_ARG}"

    const val DOWNLOADS = "downloads"

    fun discourse(id: String): String = "discourse/${android.net.Uri.encode(id)}"

    fun category(name: String): String = "category/${android.net.Uri.encode(name)}"

    fun topic(id: String): String = "topic/${android.net.Uri.encode(id)}"

    fun collection(kind: CollectionKind): String = "collection/${kind.name}"

    fun browse(kind: BrowseKind): String = "browse/${kind.name}"

    fun playlist(id: Long): String = "playlist/$id"
}

/**
 * The two ways into the library that list *groupings* rather than series.
 *
 * Both home rails scroll horizontally and hold everything they have, so "See
 * all" is not about hidden items — it is about seeing the shape of the library
 * at a glance instead of thumbing sideways through thirty tiles.
 */
enum class BrowseKind(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
) {
    TOPICS(R.string.home_themes, R.string.home_themes_subtitle),
    CATEGORIES(R.string.home_categories, R.string.home_categories_subtitle),
}

/**
 * The list-style destinations reachable from "See all" and from Library.
 *
 * Each carries the resource its heading is drawn from rather than the heading
 * itself: these names appear on screen, so they have to follow the app
 * language like everything else.
 */
enum class CollectionKind(@StringRes val titleRes: Int) {
    RECENTLY_ADDED(R.string.home_recent),
    POPULAR(R.string.home_popular),
    MEDITATION(R.string.home_meditation),
    TRENDING(R.string.home_trending),
    RECOMMENDED(R.string.home_recommended),
    ALL_SERIES(R.string.library_all_series),
    ALL_SERIES_ENGLISH(R.string.discourse_language_english),
    ALL_SERIES_HINDI(R.string.discourse_language_hindi),
    FAVOURITES(R.string.library_favourites),
    HISTORY(R.string.library_history),
    CONTINUE_LISTENING(R.string.home_continue),
}

/** The five bottom-navigation tabs. */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(Routes.HOME, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home),
    RADIO(Routes.RADIO, R.string.nav_radio, Icons.Rounded.Radio, Icons.Outlined.Radio),
    SEARCH(Routes.SEARCH, R.string.nav_search, Icons.Rounded.Search, Icons.Outlined.Search),
    LIBRARY(Routes.LIBRARY, R.string.nav_library, Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
}
