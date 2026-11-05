package com.sannyas.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sannyas.player.core.designsystem.theme.OshoTheme
import com.sannyas.player.core.designsystem.theme.ThemeMode
import com.sannyas.player.core.locale.AppLocale
import com.sannyas.player.core.review.ReviewPrompter
import com.sannyas.player.playback.VoiceSearch
import com.sannyas.player.ui.OshoApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var reviewPrompter: ReviewPrompter
    @Inject lateinit var voiceSearch: VoiceSearch

    /**
     * Binds the whole activity — not just Compose — to the chosen app language.
     *
     * Doing it here rather than overriding `LocalContext` keeps `LocalContext`
     * an activity context, which the share sheet and every dialog rely on. The
     * cost is that a language change has to `recreate()`; the navigation back
     * stack is saveable, so the user lands back where they were.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePlayFromSearch(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Hold the splash until settings have resolved, so the app never paints
        // the wrong theme for a frame.
        splash.setKeepOnScreenCondition { !viewModel.isReady }

        handlePlayFromSearch(intent)
        // enableEdgeToEdge() already clears decor fitting and applies the
        // transparent system-bar styling; calling setDecorFitsSystemWindows
        // afterwards only repeated it.
        enableEdgeToEdge()

        setContent {
            val settings by viewModel.settingsState.collectAsStateWithLifecycle()

            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> resources.configuration.isNightMode()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Icons must contrast with whatever sits behind the system bars.
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            // statusBarColor and navigationBarColor used to be set transparent
            // here. Both are deprecated and are no-ops from Android 15, and
            // Play flags them on the release; enableEdgeToEdge() is what
            // actually makes the bars transparent now.

            // Collected with a lifecycle, so a talk that ends while the phone
            // is in a pocket asks at the next moment the app is actually on
            // screen rather than into the void.
            val reviewPending by reviewPrompter.pending.collectAsStateWithLifecycle()
            LaunchedEffect(reviewPending) {
                if (reviewPending) reviewPrompter.launchFlow(this@MainActivity)
            }

            OshoTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
                OshoApp()
            }
        }
    }
}

/**
 * "Play <something>" arriving from Assistant, Android Auto or a long-pressed
 * headset button.
 *
 * `singleTask` means a second one lands in [onNewIntent] rather than a new
 * activity, so both entry points funnel here.
 */
private fun MainActivity.handlePlayFromSearch(intent: android.content.Intent?) {
    if (intent?.action != android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return
    voiceSearch.play(intent.getStringExtra(android.app.SearchManager.QUERY))
}

private fun android.content.res.Configuration.isNightMode(): Boolean =
    (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES