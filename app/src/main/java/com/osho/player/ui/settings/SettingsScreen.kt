package com.osho.player.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.graphics.Color
import com.osho.player.core.designsystem.component.pressScale
import com.osho.player.core.designsystem.component.rememberInteraction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osho.player.R
import com.osho.player.BuildConfig
import com.osho.player.core.common.formatBytes
import com.osho.player.core.designsystem.component.AuroraBackground
import com.osho.player.core.donate.Donate
import com.osho.player.core.designsystem.component.verticalScrollbar
import com.osho.player.core.designsystem.theme.OshoShapeTokens
import com.osho.player.data.local.datastore.MAX_PARALLEL_DOWNLOADS_LIMIT
import com.osho.player.core.designsystem.theme.ThemeMode
import com.osho.player.core.locale.AppLanguage
import com.osho.player.core.locale.AppLocale
import com.osho.player.core.locale.findActivity
import com.osho.player.core.locale.labelRes
import com.osho.player.domain.model.ContentLanguage
import com.osho.player.ui.player.formatSpeed

/**
 * Settings.
 *
 * Every choice is a single row showing its current value; picking a different
 * one happens in a dialog. The previous layout put chip groups inline, which
 * meant a screenful of small tap targets sitting directly under a scrolling
 * finger — easy to change something by accident. One row, one target, and the
 * only inline controls left are switches.
 */
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val cacheBytes by viewModel.cacheSize.collectAsStateWithLifecycle()
    val message by viewModel.transientMessage.collectAsStateWithLifecycle()
    val appLanguage by AppLocale.language.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollState = rememberLazyListState()

    // Resolved up front: the confirm/info models are plain data built inside
    // click lambdas, which are not composable scopes.
    val clearCacheTitle = stringResource(R.string.settings_clear_cache_title)
    val clearCacheMessage = stringResource(R.string.settings_clear_cache_message)
    val clearHistoryTitle = stringResource(R.string.settings_clear_history_title)
    val clearHistoryMessage = stringResource(R.string.settings_clear_history_message)
    val actionClear = stringResource(R.string.action_clear)
    val offLabel = stringResource(R.string.settings_timer_off)
    val donateNote = stringResource(R.string.settings_donate)
    val aboutTitle = stringResource(R.string.app_name)
    val aboutBody = stringResource(R.string.settings_about_body, APP_VERSION)

    var picker by remember { mutableStateOf<Picker?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var info by remember { mutableStateOf<InfoDialogContent?>(null) }
    var donating by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2_000)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AuroraBackground(modifier = Modifier.fillMaxSize(), alpha = 0.18f)

        LazyColumn(
            state = scrollState,
            // `statusBarsPadding` on the list, not `contentPadding`: content
            // padding is inside the scrollable area, so rows still travel up
            // into it and get drawn behind the clock. Insetting the viewport
            // itself is what actually stops them.
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScrollbar(scrollState),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "title") {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(start = 4.dp, top = 24.dp, bottom = 4.dp)
                        .semantics { heading() },
                )
            }

            // First on the screen on purpose. It is also the only row here that
            // asks for anything, so it stays a plain row rather than a banner —
            // this audience would bridle at being sold to.
            if (Donate.isConfigured) {
                item(key = "donate") {
                    SettingsGroup(title = stringResource(R.string.settings_support)) {
                        ValueRow(
                            icon = Icons.Rounded.Favorite,
                            title = stringResource(R.string.settings_donate),
                            value = "",
                            subtitle = stringResource(R.string.settings_donate_subtitle),
                            onClick = { donating = true },
                        )
                    }
                }
            }

            item(key = "language") {
                SettingsGroup(title = stringResource(R.string.settings_language)) {
                    ValueRow(
                        icon = Icons.Rounded.Translate,
                        title = stringResource(R.string.settings_app_language),
                        value = stringResource(appLanguage.labelRes),
                        onClick = { picker = Picker.AppLanguage },
                    )
                    ValueRow(
                        icon = Icons.Rounded.RecordVoiceOver,
                        title = stringResource(R.string.language_title),
                        value = stringResource(settings.contentLanguage.labelRes),
                        onClick = { picker = Picker.ContentLanguage },
                    )
                }
            }

            item(key = "appearance") {
                SettingsGroup(title = stringResource(R.string.settings_appearance)) {
                    ValueRow(
                        icon = Icons.Rounded.Contrast,
                        title = stringResource(R.string.settings_theme),
                        value = stringResource(settings.themeMode.labelRes),
                        onClick = { picker = Picker.Theme },
                    )
                    SwitchRow(
                        icon = Icons.Rounded.Palette,
                        title = stringResource(R.string.settings_material_you),
                        subtitle = stringResource(R.string.settings_material_you_subtitle),
                        checked = settings.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                }
            }

            item(key = "playback") {
                SettingsGroup(title = stringResource(R.string.settings_playback)) {
                    ValueRow(
                        icon = Icons.Rounded.Speed,
                        title = stringResource(R.string.settings_default_speed),
                        value = formatSpeed(settings.defaultPlaybackSpeed),
                        onClick = { picker = Picker.Speed },
                    )
                    ValueRow(
                        icon = Icons.Rounded.Bedtime,
                        title = stringResource(R.string.settings_default_timer),
                        value = settings.defaultSleepTimerMinutes
                            .let { if (it == 0) offLabel else "$it min" },
                        onClick = { picker = Picker.SleepTimer },
                    )
                    SwitchRow(
                        icon = Icons.Rounded.Nightlight,
                        title = stringResource(R.string.settings_dim_player),
                        subtitle = stringResource(R.string.settings_dim_player_subtitle),
                        checked = settings.dimPlayerWhenIdle,
                        onCheckedChange = viewModel::setDimPlayerWhenIdle,
                    )
                    SwitchRow(
                        icon = Icons.Rounded.VolumeUp,
                        title = stringResource(R.string.settings_skip_silence),
                        subtitle = stringResource(R.string.settings_skip_silence_subtitle),
                        checked = settings.skipSilence,
                        onCheckedChange = viewModel::setSkipSilence,
                    )
                }
            }

            item(key = "downloads") {
                SettingsGroup(title = stringResource(R.string.settings_downloads)) {
                    ValueRow(
                        icon = Icons.Rounded.Downloading,
                        title = stringResource(R.string.settings_parallel_downloads),
                        value = settings.maxParallelDownloads.toString(),
                        subtitle = stringResource(R.string.settings_parallel_downloads_subtitle),
                        onClick = { picker = Picker.ParallelDownloads },
                    )
                    SwitchRow(
                        icon = Icons.Rounded.Wifi,
                        title = stringResource(R.string.settings_wifi_only),
                        subtitle = stringResource(R.string.settings_wifi_only_subtitle),
                        checked = settings.downloadOverWifiOnly,
                        onCheckedChange = viewModel::setDownloadOverWifiOnly,
                    )
                }
            }

            item(key = "storage") {
                SettingsGroup(title = stringResource(R.string.settings_storage)) {
                    ValueRow(
                        icon = Icons.Rounded.CleaningServices,
                        title = stringResource(R.string.settings_clear_cache),
                        value = cacheBytes.formatBytes(),
                        onClick = {
                            confirm = Confirm(
                                title = clearCacheTitle,
                                message = clearCacheMessage,
                                label = actionClear,
                                action = viewModel::clearCache,
                            )
                        },
                    )
                    ValueRow(
                        icon = Icons.Rounded.DeleteOutline,
                        title = stringResource(R.string.settings_clear_history),
                        value = "",
                        onClick = {
                            confirm = Confirm(
                                title = clearHistoryTitle,
                                message = clearHistoryMessage,
                                label = actionClear,
                                action = viewModel::clearHistory,
                            )
                        },
                    )
                }
            }

            item(key = "about") {
                SettingsGroup(title = stringResource(R.string.settings_about)) {
                    ValueRow(
                        icon = Icons.Rounded.Info,
                        title = stringResource(R.string.settings_about_app),
                        value = APP_VERSION,
                        onClick = { info = InfoDialogContent(aboutTitle, aboutBody) },
                    )
                }
            }

            item(key = "developer") {
                SettingsGroup(title = stringResource(R.string.settings_developer)) {
                    ValueRow(
                        icon = Icons.Rounded.Code,
                        title = stringResource(R.string.settings_developer_name),
                        value = "",
                        subtitle = stringResource(R.string.settings_developer_site),
                        onClick = { context.openUrl(DEVELOPER_URL) },
                    )
                }
            }

            item(key = "footer") {
                Text(
                    text = stringResource(R.string.settings_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(OshoShapeTokens.Pill)
                    .background(MaterialTheme.colorScheme.inverseSurface)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    text = message?.let { stringResource(it) }.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }
    }

    // ---- Dialogs ----------------------------------------------------------

    when (picker) {
        Picker.AppLanguage -> ChoiceDialog(
            title = stringResource(R.string.settings_app_language),
            options = AppLanguage.entries.map { it to stringResource(it.labelRes) },
            selected = appLanguage,
            onSelect = { chosen ->
                picker = null
                if (chosen != appLanguage) {
                    AppLocale.set(context, chosen)
                    // Resources are bound to the activity's base context, so the
                    // only way to redraw in the new language is to rebuild it.
                    // The navigation back stack is saveable, so this lands the
                    // listener back on Settings.
                    context.findActivity()?.recreate()
                }
            },
            onDismiss = { picker = null },
        )

        Picker.ContentLanguage -> ChoiceDialog(
            title = stringResource(R.string.language_title),
            options = ContentLanguage.entries.map { it to stringResource(it.labelRes) },
            selected = settings.contentLanguage,
            onSelect = { viewModel.setContentLanguage(it); picker = null },
            onDismiss = { picker = null },
        )

        Picker.ParallelDownloads -> ChoiceDialog(
            title = stringResource(R.string.settings_parallel_downloads),
            options = (1..MAX_PARALLEL_DOWNLOADS_LIMIT).map { it to it.toString() },
            selected = settings.maxParallelDownloads,
            onSelect = { viewModel.setMaxParallelDownloads(it); picker = null },
            onDismiss = { picker = null },
        )

        Picker.Theme -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.map { it to stringResource(it.labelRes) },
            selected = settings.themeMode,
            onSelect = { viewModel.setThemeMode(it); picker = null },
            onDismiss = { picker = null },
        )

        Picker.Speed -> ChoiceDialog(
            title = stringResource(R.string.settings_default_speed),
            options = SPEEDS.map { it to formatSpeed(it) },
            selected = SPEEDS.minByOrNull { kotlin.math.abs(it - settings.defaultPlaybackSpeed) }
                ?: 1f,
            onSelect = { viewModel.setDefaultSpeed(it); picker = null },
            onDismiss = { picker = null },
        )

        Picker.SleepTimer -> ChoiceDialog(
            title = stringResource(R.string.settings_default_timer),
            options = TIMERS.map { it to if (it == 0) offLabel else "$it min" },
            selected = settings.defaultSleepTimerMinutes,
            onSelect = { viewModel.setDefaultSleepTimer(it); picker = null },
            onDismiss = { picker = null },
        )

        null -> Unit
    }

    confirm?.let { pending ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(pending.title, style = MaterialTheme.typography.titleLarge) },
            text = { Text(pending.message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { pending.action(); confirm = null }) {
                    Text(pending.label, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
        )
    }

    if (donating) {
        DonateDialog(
            onUpi = { amount -> Donate.openUpi(context, donateNote, amount); donating = false },
            onInternational = { amount ->
                Donate.openInternational(context, amount)
                donating = false
            },
            onDismiss = { donating = false },
        )
    }

    info?.let { content ->
        AlertDialog(
            onDismissRequest = { info = null },
            title = { Text(content.title, style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = content.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { info = null }) { Text(stringResource(R.string.action_close)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
        )
    }
}

// ------------------------------------------------------------------ pieces

private enum class Picker { AppLanguage, ContentLanguage, Theme, Speed, SleepTimer, ParallelDownloads }

private data class Confirm(
    val title: String,
    val message: String,
    val label: String,
    val action: () -> Unit,
)

private data class InfoDialogContent(val title: String, val body: String)

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 12.dp)
                .semantics { heading() },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(OshoShapeTokens.Card)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            content()
        }
    }
}

/** A row showing a setting and its current value; tapping opens the picker. */
@Composable
private fun ValueRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    /** For values too long to sit on the title line, such as a URL. */
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(title, subtitle, value.takeIf { it.isNotBlank() })
                    .joinToString(", ")
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (value.isNotBlank()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$title. $subtitle" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RowIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(OshoShapeTokens.Pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
    }
}

/** Single-choice dialog shared by every picker on this screen. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEach { (value, label) ->
                    val isSelected = value == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(OshoShapeTokens.Pill)
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelect(value) },
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
    )
}

private val SPEEDS = listOf(0.5f, 0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f)
private val TIMERS = listOf(0, 5, 10, 15, 30, 45, 60, 90)

private val APP_VERSION: String = BuildConfig.VERSION_NAME

private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }

private const val DEVELOPER_URL = "https://warlordsoftwares.com/rishabh-bhardwaj/"

/**
 * Hands the link to whatever the user browses with.
 *
 * Wrapped because a device with no browser at all would otherwise crash the
 * settings screen on a tap — rare, but not worth a crash.
 */
private fun Context.openUrl(url: String) {
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/**
 * Amounts, not a payment form.
 *
 * Each chip opens the giver's own payment app with that figure already in it,
 * so the whole transaction happens somewhere this app never sees. "Other
 * amount" opens the same app with the field empty, which is the honest default
 * for a donation — the presets are a convenience, not a suggestion of what is
 * expected.
 */
@Composable
private fun DonateDialog(
    onUpi: (Int?) -> Unit,
    onInternational: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_donate),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (Donate.hasUpi) {
                    DonateSection(
                        title = stringResource(R.string.donate_upi),
                        detail = stringResource(R.string.donate_upi_detail),
                        amounts = Donate.UPI_PRESETS.map { it to "₹$it" },
                        onAmount = onUpi,
                    )
                }
                if (Donate.hasInternational) {
                    DonateSection(
                        title = stringResource(R.string.donate_international),
                        detail = stringResource(R.string.donate_international_detail),
                        amounts = Donate.INTERNATIONAL_PRESETS.map { it to "$$it" },
                        onAmount = onInternational,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun DonateSection(
    title: String,
    detail: String,
    amounts: List<Pair<Int, String>>,
    onAmount: (Int?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            amounts.forEach { (amount, label) ->
                DonateChip(label = label, onClick = { onAmount(amount) })
            }
            DonateChip(
                label = stringResource(R.string.donate_other_amount),
                highlighted = false,
                onClick = { onAmount(null) },
            )
        }
    }
}

@Composable
private fun DonateChip(
    label: String,
    onClick: () -> Unit,
    highlighted: Boolean = true,
) {
    val interaction = rememberInteraction()

    Box(
        modifier = Modifier
            .sizeIn(minHeight = 44.dp)
            .pressScale(interaction)
            .clip(OshoShapeTokens.Pill)
            .background(
                if (highlighted) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    Color.Transparent
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (highlighted) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}
