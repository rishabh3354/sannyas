package com.osho.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.osho.player.R
import com.osho.player.playback.PlaybackRecovery
import com.osho.player.ui.components.BottomBarHeight
import com.osho.player.ui.components.LanguageOnboardingDialog
import com.osho.player.ui.components.MiniPlayer
import com.osho.player.ui.components.OshoBottomBar
import com.osho.player.ui.components.RequestNotificationPermission
import com.osho.player.ui.navigation.OshoNavHost
import com.osho.player.ui.navigation.TopLevelDestination
import com.osho.player.ui.player.PlayerScreen
import com.osho.player.ui.player.PlayerViewModel

/**
 * The app shell.
 *
 * Owns the three things that persist across every screen: the navigation host,
 * the mini player, and the full player overlay.
 *
 * The full player is drawn as a sibling overlay rather than as a navigation
 * destination. That is what allows it to expand *over* the bottom bar with the
 * mini player still beneath it, and to be dismissed without disturbing the
 * back stack of whichever tab the user was browsing.
 */
@Composable
fun OshoApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val mainViewModel: com.osho.player.MainViewModel = hiltViewModel()
    val settings by mainViewModel.settingsState.collectAsStateWithLifecycle()

    val playerViewModel: PlayerViewModel = hiltViewModel()
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()

    var playerExpanded by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Playback errors surface as a snackbar wherever the user happens to be.
    val errorText = playerState.errorMessageRes?.let { stringResource(it) }
    LaunchedEffect(errorText) {
        errorText?.let { message ->
            snackbarHostState.showSnackbar(message)
            playerViewModel.consumeError()
        }
    }

    // Transient drops are retried in silence; this is the point where that has
    // stopped being true and the listener deserves to be told.
    val recovery by playerViewModel.recovery.collectAsStateWithLifecycle()
    // Said once, on the first failure, rather than after four minutes of
    // silence: someone who just tapped a talk and got nothing needs to know now
    // that the wait is the site's fault and not theirs.
    val retryingText = stringResource(R.string.player_source_down_retrying)
    LaunchedEffect(recovery.attempt, recovery.outage) {
        if (recovery.attempt == 1 &&
            recovery.outage == PlaybackRecovery.Outage.SOURCE_UNREACHABLE
        ) {
            snackbarHostState.showSnackbar(retryingText)
        }
    }

    val offlineText = stringResource(R.string.player_error_network)
    val sourceDownText = stringResource(R.string.player_error_source_down)
    LaunchedEffect(recovery.gaveUp, recovery.outage) {
        if (!recovery.gaveUp) return@LaunchedEffect
        snackbarHostState.showSnackbar(
            when (recovery.outage) {
                PlaybackRecovery.Outage.SOURCE_UNREACHABLE -> sourceDownText
                else -> offlineText
            },
        )
    }

    // Collapse the player before letting back fall through to navigation.
    BackHandler(enabled = playerExpanded) {
        playerViewModel.persistPosition()
        playerExpanded = false
    }

    // The media notification is the lock-screen and Bluetooth control surface,
    // so ask for it up front rather than at first play.
    // Asked once, before anything else, because half the catalogue is in a
    // language the listener may not follow.
    if (!settings.languageChosen) {
        LanguageOnboardingDialog(onSelect = mainViewModel::chooseContentLanguage)
    } else {
        // Held back until the language has been chosen: firing both at once
        // stacks the system permission sheet on top of our dialog.
        RequestNotificationPermission()
    }

    val destinations = remember { TopLevelDestination.entries }
    val isTopLevel = destinations.any { it.route == currentRoute }

    // Content sits above the bottom bar and, when active, the mini player.
    val bottomInset = BottomBarHeight + if (playerState.isActive) MiniPlayerHeight else 0.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            // The window background is a fixed near-black (it has to match the
            // splash). Without painting the themed background here, light theme
            // showed that dark grey through every gap between cards.
            .background(MaterialTheme.colorScheme.background),
    ) {
        OshoNavHost(
            navController = navController,
            contentPadding = PaddingValues(bottom = bottomInset),
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AnimatedVisibility(
                visible = playerState.isActive && !playerExpanded,
                enter = slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) { it } + fadeIn(tween(180)),
                exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(140)),
            ) {
                MiniPlayer(
                    // Only dismissible when stopped: a sideways swipe during
                    // playback is far more likely to be a stray gesture than a
                    // deliberate "stop and forget this".
                    dismissEnabled = !playerState.isPlaying,
                    state = playerState,
                    onExpand = { playerExpanded = true },
                    onPlayPause = playerViewModel::togglePlayPause,
                    onNext = playerViewModel::next,
                    onDismiss = playerViewModel::stop,
                )
            }

            AnimatedVisibility(
                visible = isTopLevel,
                enter = slideInVertically(tween(240)) { it } + fadeIn(),
                exit = slideOutVertically(tween(200)) { it } + fadeOut(),
            ) {
                OshoBottomBar(
                    destinations = destinations,
                    currentRoute = currentRoute,
                    onNavigate = { destination ->
                        navController.navigateToTab(destination)
                    },
                )
            }
        }

        // The expand animation: the sheet rises from the bottom while scaling up
        // slightly, which reads as the mini player growing rather than as a new
        // screen sliding in.
        AnimatedVisibility(
            visible = playerExpanded,
            enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) { it } + scaleIn(tween(320), initialScale = 0.92f) + fadeIn(tween(160)),
            exit = slideOutVertically(tween(280)) { it } +
                scaleOut(tween(280), targetScale = 0.94f) +
                fadeOut(tween(200)),
        ) {
            PlayerScreen(
                viewModel = playerViewModel,
                onCollapse = {
                    playerViewModel.persistPosition()
                    playerExpanded = false
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = bottomInset + 12.dp, start = 16.dp, end = 16.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.medium,
            )
        }
    }
}

/** Height reserved for the mini player, including its outer spacing. */
private val MiniPlayerHeight = 74.dp

/**
 * Tab switching that behaves the way users expect: one entry per tab in the
 * back stack, state preserved when returning, and no growth on repeat taps.
 */
private fun androidx.navigation.NavHostController.navigateToTab(
    destination: TopLevelDestination,
) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
