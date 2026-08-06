package com.osho.player.ui.player

import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.osho.player.core.locale.findActivity
import kotlinx.coroutines.delay

/**
 * Fades the player down when it is left alone.
 *
 * A discourse is put on and then not touched for ninety minutes, usually in a
 * dark room. Leaving a bright screen sitting there is both the wrong light for
 * the moment and, on OLED, a real battery cost — so after a spell of no
 * touching the screen dims twice over: a scrim for what is drawn, and the
 * window's own backlight override for the panel itself. Either alone is half a
 * fix; the scrim cannot dim the backlight, and the backlight alone still leaves
 * bright artwork glowing.
 *
 * Any touch brings it straight back, and the touch that wakes it is swallowed
 * rather than passed through — reaching for a dark screen is how you pause a
 * talk you meant to keep playing.
 */
@Composable
fun NightDim(
    enabled: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var lastTouchAt by remember { mutableLongStateOf(0L) }
    var dimmed by remember { mutableStateOf(false) }

    val active = enabled && isPlaying

    LaunchedEffect(active, lastTouchAt) {
        dimmed = false
        if (!active) return@LaunchedEffect
        delay(IDLE_BEFORE_DIM_MS)
        dimmed = true
    }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (dimmed) SCRIM_ALPHA else 0f,
        // Slow going down so it is never the thing that catches the eye; quick
        // coming back so a tap feels answered.
        animationSpec = tween(durationMillis = if (dimmed) 2_400 else 180),
        label = "night-dim",
    )

    val activity = LocalContext.current.findActivity()

    LaunchedEffect(dimmed, activity) {
        val window = activity?.window ?: return@LaunchedEffect
        window.attributes = window.attributes.apply {
            screenBrightness = if (dimmed) {
                DIM_BRIGHTNESS
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    // Collapsing the player, or the process going away, must not leave the
    // whole device stuck at 5% brightness.
    DisposableEffect(activity) {
        onDispose {
            val window = activity?.window ?: return@onDispose
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Watched on the initial pass so every touch counts as activity,
            // including ones a control below goes on to consume.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastTouchAt = System.nanoTime()
                    }
                }
            },
    ) {
        content()

        if (scrimAlpha > 0.01f) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .then(
                        // Only swallows touches once fully dim. Mid-fade the
                        // controls are still legible, so they stay usable.
                        if (dimmed) {
                            Modifier.pointerInput(Unit) { detectTapGestures { } }
                        } else {
                            Modifier
                        },
                    )
                    .clearAndSetSemantics { },
            )
        }
    }
}

/** Long enough to survive scrubbing and reading the title, short enough to matter. */
private const val IDLE_BEFORE_DIM_MS = 25_000L

/** Leaves the artwork as a suggestion rather than a picture. */
private const val SCRIM_ALPHA = 0.86f

/** Near the panel minimum, but not off — the transport is still findable. */
private const val DIM_BRIGHTNESS = 0.05f
