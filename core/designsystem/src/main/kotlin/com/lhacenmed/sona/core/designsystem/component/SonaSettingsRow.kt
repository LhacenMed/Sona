package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** How wide a settings row's end is: a switch's, so whatever stands there lines up with the switches down a list. */
private val SettingsTrailingWidth = 52.dp

/** Seal's preference title, in the theme's own font: larger than a list's headline, so a row reads as a setting rather than an item. */
private val SettingsTitleStyle = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal, lineBreak = LineBreak.Paragraph)

/** Every settings row's title, in the theme's font - one style, whichever kind of row draws it. */
@Composable
internal fun settingsTitleStyle(): TextStyle = MaterialTheme.typography.titleLarge.merge(SettingsTitleStyle)

/** The colour of what a settings row says while it is not enabled - the one disabled look every row shares. */
@Composable
internal fun settingsDisabledColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledContentAlpha)

/** Material's disabled content: the surface's text at this much of its colour. */
private const val DisabledContentAlpha = 0.38f

/**
 * The row every settings list is built from - the app's settings and the Private Folder's alike: [title] over
 * [summary], led by [leading] and ended by [trailing], the whole row pressed as one where it does something -
 * [onClick]: hitting the text expecting it to act is the commonest way to miss a row's control. While not [enabled]
 * it stands faded and inert, its control showing its own disabled look beside it - the one disabled look every row
 * shares.
 *
 * What stands at its end - a switch, a value, an icon, a busy indicator - is centred in one column a switch wide, so
 * the ends of a list's rows line up whatever each shows. Something wider - a control beside a switch - takes the room
 * it needs from the text, its end still where every row's is.
 */
@Composable
fun SonaSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleMaxLines: Int = Int.MAX_VALUE,
    summaryMaxLines: Int = Int.MAX_VALUE,
) {
    ListItem(
        headlineContent = { Text(title, style = settingsTitleStyle(), maxLines = titleMaxLines, overflow = TextOverflow.Ellipsis) },
        supportingContent = summary?.let { { Text(it, maxLines = summaryMaxLines, overflow = TextOverflow.Ellipsis) } },
        leadingContent = leading,
        trailingContent = trailing?.let { content ->
            { Box(modifier = Modifier.widthIn(min = SettingsTrailingWidth), contentAlignment = Alignment.Center) { content() } }
        },
        colors = settingsRowColors(enabled),
        modifier = if (onClick == null) modifier else modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

/**
 * What a setting stands at, at a [SonaSettingsRow]'s end - "On", "Off". In the row's own colour for what stands at its
 * end, so it fades with the row as a switch does.
 */
@Composable
fun SonaSettingsValue(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun settingsRowColors(enabled: Boolean): ListItemColors {
    if (enabled) return ListItemDefaults.colors()
    val disabledColor = settingsDisabledColor()
    return ListItemDefaults.colors(
        headlineColor = disabledColor,
        supportingColor = disabledColor,
        leadingIconColor = disabledColor,
        trailingIconColor = disabledColor,
    )
}
