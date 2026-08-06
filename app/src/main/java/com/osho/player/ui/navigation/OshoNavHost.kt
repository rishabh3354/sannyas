package com.osho.player.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.osho.player.ui.browse.BrowseScreen
import com.osho.player.ui.detail.DiscourseDetailScreen
import com.osho.player.ui.downloads.DownloadsScreen
import com.osho.player.ui.home.HomeScreen
import com.osho.player.ui.library.CollectionScreen
import com.osho.player.ui.library.LibraryScreen
import com.osho.player.ui.playlist.PlaylistDetailScreen
import com.osho.player.ui.playlist.PlaylistsScreen
import com.osho.player.ui.radio.RadioScreen
import com.osho.player.ui.search.SearchScreen
import com.osho.player.ui.settings.SettingsScreen

/**
 * The navigation graph.
 *
 * Transitions follow Material's shared-axis motion: top-level tab switches use
 * a fade-through (no directional movement, because tabs are siblings), while
 * pushing a detail screen slides along the X axis. Durations are deliberately
 * short — 300 ms or less — so the app feels immediate rather than showy.
 */
@Composable
fun OshoNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
        enterTransition = { fadeThroughEnter() },
        exitTransition = { fadeThroughExit() },
        popEnterTransition = { fadeThroughEnter() },
        popExitTransition = { fadeThroughExit() },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                contentPadding = contentPadding,
                onDiscourseClick = { navController.navigateSingleTop(Routes.discourse(it)) },
                onCategoryClick = { navController.navigateSingleTop(Routes.category(it)) },
                onTopicClick = { navController.navigateSingleTop(Routes.topic(it)) },
                onSeeAll = { navController.navigateSingleTop(Routes.collection(it)) },
                onBrowseAll = { navController.navigateSingleTop(Routes.browse(it)) },
            )
        }

        composable(Routes.RADIO) {
            RadioScreen(contentPadding = contentPadding)
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                contentPadding = contentPadding,
                onDiscourseClick = { navController.navigateSingleTop(Routes.discourse(it)) },
                onCategoryClick = { navController.navigateSingleTop(Routes.category(it)) },
            )
        }

        composable(Routes.LIBRARY) {
            LibraryScreen(
                contentPadding = contentPadding,
                onDiscourseClick = { navController.navigateSingleTop(Routes.discourse(it)) },
                onCollectionClick = { navController.navigateSingleTop(Routes.collection(it)) },
                onDownloadsClick = { navController.navigateSingleTop(Routes.DOWNLOADS) },
                onPlaylistsClick = { navController.navigateSingleTop(Routes.PLAYLISTS) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(contentPadding = contentPadding)
        }

        detailComposable(Routes.DISCOURSE, Routes.DISCOURSE_ARG) {
            DiscourseDetailScreen(
                contentPadding = contentPadding,
                onBack = navController::popBackStack,
            )
        }

        detailComposable(Routes.CATEGORY, Routes.CATEGORY_ARG) {
            CollectionScreen(
                contentPadding = contentPadding,
                onBack = navController::popBackStack,
                onDiscourseClick = { navController.navigateSingleTop(Routes.discourse(it)) },
                onCollectionClick = { navController.navigateSingleTop(Routes.collection(it)) },
            )
        }

        detailComposable(Routes.TOPIC, Routes.TOPIC_ARG) {
            CollectionScreen(
                contentPadding = contentPadding,
                onBack = navController::popBackStack,
                onDiscourseClick = { navController.navigateSingleTop(Routes.discourse(it)) },
                onCollectionClick = { navController.navigateSingleTop(Routes.collection(it)) },
            )
        }

        detailComposable(Routes.COLLECTION, Routes.COLLECTION_ARG) {
            CollectionScreen(
                contentPadding = contentPadding,
                onBack = navController::popBackStack,
                onDiscourseClick = { navController.navigateSingleTop(Routes.discourse(it)) },
                // Plain `navigate`, not the single-top helper. One collection
                // opening another — "All Series" handing off to its English
                // half — is the same destination node with a different
                // argument, and `launchSingleTop` compares node ids: it would
                // quietly reuse the entry we are standing on and nothing would
                // appear to happen.
                onCollectionClick = { navController.navigate(Routes.collection(it)) },
            )
        }

        detailComposable(Routes.BROWSE, Routes.BROWSE_ARG) {
            BrowseScreen(
                contentPadding = contentPadding,
                onBack = navController::popBackStack,
                onTopicClick = { navController.navigateSingleTop(Routes.topic(it)) },
                onCategoryClick = { navController.navigateSingleTop(Routes.category(it)) },
            )
        }

        detailComposable(Routes.PLAYLISTS, argument = null) {
            PlaylistsScreen(
                contentPadding = contentPadding,
                onBack = navController::popBackStack,
                onOpen = { navController.navigateSingleTop(Routes.playlist(it)) },
            )
        }

        detailComposable(Routes.PLAYLIST, Routes.PLAYLIST_ARG) {
            PlaylistDetailScreen(
                contentPadding = contentPadding,
                onBack = navController::popBackStack,
            )
        }

        detailComposable(Routes.DOWNLOADS, argument = null) {
            DownloadsScreen(
                contentPadding = contentPadding,
                onBack = navController::popBackStack,
            )
        }
    }
}

/**
 * Registers a pushed screen with shared-axis-X motion.
 *
 * Declaring the argument as a nullable string type keeps encoded ids (which can
 * contain slashes once encoded) working without a custom NavType.
 */
private fun NavGraphBuilder.detailComposable(
    route: String,
    argument: String?,
    content: @Composable () -> Unit,
) {
    composable(
        route = route,
        arguments = argument?.let {
            listOf(navArgument(it) { type = NavType.StringType })
        } ?: emptyList(),
        enterTransition = {
            slideInHorizontally(SlideSpec) { it / 6 } + fadeIn(FadeSpec)
        },
        exitTransition = {
            slideOutHorizontally(SlideSpec) { -it / 8 } + fadeOut(FadeSpec)
        },
        popEnterTransition = {
            slideInHorizontally(SlideSpec) { -it / 8 } + fadeIn(FadeSpec)
        },
        popExitTransition = {
            slideOutHorizontally(SlideSpec) { it / 6 } + fadeOut(FadeSpec)
        },
    ) { content() }
}

private val SlideSpec = tween<androidx.compose.ui.unit.IntOffset>(
    durationMillis = 300,
    easing = FastOutSlowInEasing,
)
private val FadeSpec = tween<Float>(durationMillis = 220)

/**
 * Fade-through: the outgoing screen fades and shrinks a touch, the incoming one
 * fades in slightly enlarged. No horizontal movement, because the tabs are
 * peers and a slide would imply a hierarchy that is not there.
 */
private fun AnimatedContentTransitionScope<*>.fadeThroughEnter() =
    fadeIn(tween(280, delayMillis = 60)) + scaleIn(tween(280, delayMillis = 60), initialScale = 0.96f)

private fun AnimatedContentTransitionScope<*>.fadeThroughExit() =
    fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 1.02f)

/**
 * Navigates without stacking duplicates of the same destination, which is what
 * repeated taps on a card would otherwise produce.
 */
private fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) { launchSingleTop = true }
}
