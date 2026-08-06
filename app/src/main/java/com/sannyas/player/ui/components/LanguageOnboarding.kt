package com.sannyas.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.sannyas.player.R
import com.sannyas.player.core.designsystem.component.glow
import com.sannyas.player.core.designsystem.component.pressScale
import com.sannyas.player.core.designsystem.component.rememberInteraction
import com.sannyas.player.core.designsystem.theme.OshoShapeTokens
import com.sannyas.player.domain.model.ContentLanguage

/**
 * First-run language chooser.
 *
 * Osho spoke roughly half these discourses in Hindi and half in English, and the
 * two are of little use to someone who does not follow the other — so this is
 * asked once, up front, rather than leaving a new listener to scroll past
 * hundreds of unintelligible series. It is not dismissible by tapping away:
 * every option is a valid answer, so there is nothing to escape from, and the
 * copy makes clear it can be changed later.
 */
@Composable
fun LanguageOnboardingDialog(
    onSelect: (ContentLanguage) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* A choice is required; all options are equally valid. */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        icon = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .glow(MaterialTheme.colorScheme.primary, radius = 18.dp, alpha = 0.30f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))

                LanguageOption(
                    label = stringResource(R.string.onboarding_english),
                    detail = stringResource(R.string.onboarding_english_detail),
                    onClick = { onSelect(ContentLanguage.ENGLISH) },
                )
                LanguageOption(
                    label = stringResource(R.string.onboarding_hindi),
                    detail = stringResource(R.string.onboarding_hindi_detail),
                    onClick = { onSelect(ContentLanguage.HINDI) },
                )
                LanguageOption(
                    label = stringResource(R.string.onboarding_both),
                    detail = stringResource(R.string.onboarding_both_detail),
                    highlighted = true,
                    onClick = { onSelect(ContentLanguage.ALL) },
                )
            }
        },
        confirmButton = {},
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@Composable
private fun LanguageOption(
    label: String,
    detail: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    val interaction = rememberInteraction()
    val description = stringResource(R.string.cd_item_detail, label, detail)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pressScale(interaction)
            .clip(OshoShapeTokens.Pill)
            .background(
                if (highlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 22.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (highlighted) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlighted) {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
