@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.settings.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes

/** A small label beside a [SettingsHero]'s label - in the tertiary colour when [isAccented]. */
class SettingsHeroBadge(val text: String, val isAccented: Boolean = false)

/**
 * One of a [SettingsHero]'s buttons. While [isBusy] - what it started still under way - it shows so in place
 * of its icon and cannot be pressed again.
 */
class SettingsHeroAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val isBusy: Boolean = false,
)

/**
 * Where something a settings screen is about stands, over its first section - ArchiveTune's update card:
 * state and what to do about it, where a [SettingsHeader] is only an identity - [icon] on Material's soft burst, [label] and its [badge], [title]
 * large, [supporting] under it, then its [actions], each across the card - the first filled, the rest text.
 *
 * [isHighlighted] puts the burst in the primary colour, for something that wants the eye - an update waiting.
 */
@Composable
fun SettingsHero(
    icon: Painter,
    title: String,
    supporting: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    badge: SettingsHeroBadge? = null,
    isHighlighted: Boolean = false,
    actions: List<SettingsHeroAction> = emptyList(),
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = MaterialShapes.SoftBurst.toShape(),
                    color = if (isHighlighted) colorScheme.primaryContainer else colorScheme.secondaryContainer,
                    contentColor = if (isHighlighted) colorScheme.onPrimaryContainer else colorScheme.onSecondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (label != null || badge != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = label.orEmpty(),
                                style = MaterialTheme.typography.labelLarge,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            badge?.let { HeroBadge(it) }
                        }
                    }
                    Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(text = supporting, style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
                }
            }
            if (actions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    actions.forEachIndexed { index, action ->
                        val content: @Composable () -> Unit = { HeroActionContent(action) }
                        val buttonModifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        val enabled = action.enabled && !action.isBusy
                        if (index == 0) {
                            Button(onClick = action.onClick, enabled = enabled, shapes = buttonPressShapes(), modifier = buttonModifier) {
                                content()
                            }
                        } else {
                            TextButton(onClick = action.onClick, enabled = enabled, shapes = buttonPressShapes(), modifier = buttonModifier) {
                                content()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroBadge(badge: SettingsHeroBadge) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (badge.isAccented) colorScheme.tertiaryContainer else colorScheme.secondaryContainer,
        contentColor = if (badge.isAccented) colorScheme.onTertiaryContainer else colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = badge.text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun HeroActionContent(action: SettingsHeroAction) {
    if (action.isBusy) {
        LoadingIndicator(modifier = Modifier.size(18.dp))
    } else {
        Icon(action.icon, contentDescription = null, modifier = Modifier.size(18.dp))
    }
    Spacer(modifier = Modifier.width(8.dp))
    Text(text = action.label)
}
