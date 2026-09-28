@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/*
 * Every row below that holds a value holds it only until the screen is closed. The settings tree is
 * being laid out before any of it is connected to storage, and a control that cannot move reads as
 * broken rather than as unfinished. Connecting one means taking its value and its callback from a
 * ViewModel instead - the row around it does not change.
 */

/** A row that opens a screen of its own - or, while not [enabled], stands faded and inert in its place. */
@Composable
fun SettingsNavigationItem(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = icon?.let { { SettingsLeadingIcon(rememberVectorPainter(it)) } },
        colors = settingsRowColors(enabled),
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

/**
 * How wide the start of a row that leads with something is: an avatar's width, which an icon is centred in.
 * One width for every row, so a row's text starts at the same place whether it leads with an icon or with a
 * picture - Material's list item lets its text start wherever its leading content ends.
 */
private val LeadingSize = 40.dp

/** An icon at the start of a row - centred in the width every row's leading content takes. */
@Composable
fun SettingsLeadingIcon(icon: Painter) {
    Box(modifier = Modifier.size(LeadingSize), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null)
    }
}

/**
 * A row that does something at once rather than leading anywhere - clearing a cache, checking for an
 * update. Distinct from a navigation row because nothing opens, so the row carries no chevron and no
 * promise of a screen behind it.
 */
@Composable
fun SettingsActionItem(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
) {
    SettingsActionItem(
        title = title,
        summary = summary,
        onClick = {},
        modifier = modifier,
    )
}

/**
 * A row that does something when pressed. A disabled one keeps its place, faded and inert, so the
 * screen keeps one shape whether or not it can be pressed right now. While [isBusy] - what it started
 * still under way - it shows so at its end and cannot be pressed again, without fading.
 */
@Composable
fun SettingsActionItem(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isBusy: Boolean = false,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = if (isBusy) {
            { LoadingIndicator(modifier = Modifier.size(BusyIndicatorSize)) }
        } else {
            null
        },
        modifier = modifier
            .clickable(enabled = enabled && !isBusy, onClick = onClick)
            .alpha(if (enabled) 1f else DISABLED_ROW_ALPHA),
    )
}

/** How large a row's busy indicator is: an icon's size, so a row keeps its height while busy. */
private val BusyIndicatorSize = 24.dp

/**
 * A row that opens a page on the web - a project's, a person's, a commit's - led by [leadingContent] when it
 * has a [SettingsLeadingIcon] or a [SettingsAvatar]. The mark at its end says it leaves the app, where a
 * [SettingsNavigationItem] opens a screen of the app's own.
 */
@Composable
fun SettingsLinkItem(
    title: String,
    summary: String,
    url: String,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    ListItem(
        headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(summary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = leadingContent,
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = modifier.clickable { runCatching { uriHandler.openUri(url) } },
    )
}

/**
 * Someone's picture, round, where a row's icon would be - on a quiet circle until it has loaded, and on
 * that circle alone for someone who has none.
 */
@Composable
fun SettingsAvatar(url: String?) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(LeadingSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

/** Material's opacity for disabled content. */
private const val DISABLED_ROW_ALPHA = 0.38f

/** A row that only reports something - a version, a size, a total. Not a control, so not clickable. */
@Composable
fun SettingsInfoItem(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        modifier = modifier,
    )
}

/** A row that turns something on or off. */
@Composable
fun SettingsSwitchItem(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    initialValue: Boolean = false,
) {
    var checked by remember { mutableStateOf(initialValue) }
    SettingsSwitchItem(
        title = title,
        summary = summary,
        checked = checked,
        onCheckedChange = { checked = it },
        modifier = modifier,
    )
}

/** A row that turns a stored setting on or off. */
@Composable
fun SettingsSwitchItem(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    // False for a setting this device has nothing to do with: shown, as Material shows it disabled.
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
        colors = settingsRowColors(enabled),
        // The whole row, not just the switch: a settings row is one target, and hitting the text
        // expecting it to toggle is the commonest way to miss.
        modifier = modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
    )
}

/** Where something a [SettingsDownloadItem] offers stands. */
sealed interface SettingsDownload {
    data object Available : SettingsDownload

    /** [progress] from 0 to 1. */
    data class InProgress(val progress: Float) : SettingsDownload

    data object Done : SettingsDownload

    data object Failed : SettingsDownload
}

/**
 * A row for something the app downloads only for those who want it - the in-app updater's download, as a
 * row: a download button while it is not here, its progress while it arrives (pressed, it stops), and a
 * remove button once it is. A failed download offers itself again. The whole row is the one target, as a
 * switch row is.
 */
@Composable
fun SettingsDownloadItem(
    title: String,
    summary: String,
    download: SettingsDownload,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val action = when (download) {
        SettingsDownload.Available, SettingsDownload.Failed -> onDownload
        is SettingsDownload.InProgress -> onCancel
        SettingsDownload.Done -> onRemove
    }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = {
            Box(modifier = Modifier.size(DownloadControlSize), contentAlignment = Alignment.Center) {
                when (download) {
                    SettingsDownload.Available -> IconButton(onClick = onDownload) {
                        Icon(Icons.Filled.Download, contentDescription = "Download")
                    }
                    SettingsDownload.Failed -> IconButton(onClick = onDownload) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Try again")
                    }
                    is SettingsDownload.InProgress -> {
                        CircularProgressIndicator(progress = { download.progress }, modifier = Modifier.size(DownloadControlSize))
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Filled.Close, contentDescription = "Stop downloading", modifier = Modifier.size(18.dp))
                        }
                    }
                    SettingsDownload.Done -> IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove")
                    }
                }
            }
        },
        modifier = modifier.clickable(onClick = action),
    )
}

/** The size of a download row's control: a touch target, so the row keeps one height whatever it shows. */
private val DownloadControlSize = 40.dp

/** Material's disabled content: the surface's text at this much of its colour. */
private const val DISABLED_CONTENT_ALPHA = 0.38f

/** A row's colours: Material's own, or its disabled text colour while the row is not [enabled]. */
@Composable
private fun settingsRowColors(enabled: Boolean): ListItemColors {
    if (enabled) return ListItemDefaults.colors()
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA)
    return ListItemDefaults.colors(
        headlineColor = disabledColor,
        supportingColor = disabledColor,
        leadingIconColor = disabledColor,
    )
}

/**
 * A row whose value is one of a fixed set, shown beneath the title and picked from a dialog.
 *
 * The chosen option is the summary, so the row answers "what is this set to" without being opened -
 * which is the whole reason a settings list is worth scrolling.
 */
@Composable
fun SettingsChoiceItem(
    title: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    selectedIndex: Int = 0,
    icon: ImageVector? = null,
) {
    var selected by remember { mutableIntStateOf(selectedIndex) }
    var isChoosing by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(options[selected]) },
        leadingContent = icon?.let { { SettingsLeadingIcon(rememberVectorPainter(it)) } },
        modifier = modifier.clickable { isChoosing = true },
    )

    if (isChoosing) {
        SettingsChoiceDialog(
            title = title,
            options = options,
            selectedIndex = selected,
            onSelect = {
                selected = it
                isChoosing = false
            },
            onDismiss = { isChoosing = false },
        )
    }
}

/**
 * A row whose stored value is one of a fixed set, picked from a dialog. [summary] says what it is set
 * to, where the chosen option alone would say too little. While not [enabled], it still says so, faded
 * and inert.
 */
@Composable
fun SettingsChoiceItem(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String = options[selectedIndex],
    enabled: Boolean = true,
) {
    var isChoosing by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        colors = settingsRowColors(enabled),
        modifier = modifier.clickable(enabled = enabled) { isChoosing = true },
    )

    if (isChoosing) {
        SettingsChoiceDialog(
            title = title,
            options = options,
            selectedIndex = selectedIndex,
            onSelect = {
                onSelect(it)
                isChoosing = false
            },
            onDismiss = { isChoosing = false },
        )
    }
}

/**
 * A row holding a number on a continuous range.
 *
 * The slider sits under the title rather than beside it, because a slider squeezed into a trailing
 * slot is too short to aim with. [formatValue] turns the raw number into whatever unit the setting
 * is actually in - seconds, decibels, a percentage - which is the only part of a slider a reader
 * can interpret.
 */
@Composable
fun SettingsSliderItem(
    title: String,
    valueRange: ClosedFloatingPointRange<Float>,
    formatValue: (Float) -> String,
    modifier: Modifier = Modifier,
    initialValue: Float = valueRange.start,
    steps: Int = 0,
) {
    var value by remember { mutableFloatStateOf(initialValue) }
    SettingsSliderItem(
        title = title,
        value = value,
        onValueChangeFinished = { value = it },
        valueRange = valueRange,
        formatValue = formatValue,
        modifier = modifier,
        steps = steps,
    )
}

/**
 * A row holding a stored number on a continuous range.
 *
 * The slider follows the finger on its own and hands over [onValueChangeFinished] only once it is let
 * go, so dragging it writes the setting once rather than on every frame of the drag.
 */
@Composable
fun SettingsSliderItem(
    title: String,
    value: Float,
    onValueChangeFinished: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    formatValue: (Float) -> String,
    modifier: Modifier = Modifier,
    steps: Int = 0,
) {
    var draggedValue by remember(value) { mutableFloatStateOf(value) }
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(formatValue(draggedValue)) },
            modifier = Modifier.padding(horizontal = 0.dp),
        )
        Slider(
            value = draggedValue,
            onValueChange = { draggedValue = it },
            onValueChangeFinished = { onValueChangeFinished(draggedValue) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * A row holding a piece of text the user types - a list of separator characters, a folder name.
 *
 * Edited in a dialog rather than in place: a field living in the list would take focus while the
 * list is being scrolled past, and the keyboard would cover the rows underneath it.
 */
@Composable
fun SettingsTextFieldItem(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    initialValue: String = "",
) {
    var value by remember { mutableStateOf(initialValue) }
    var isEditing by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(title) },
        // The value when there is one, the explanation when there is not, so the row is never a
        // bare title with an empty line under it.
        supportingContent = { Text(value.ifEmpty { summary }) },
        modifier = modifier.clickable { isEditing = true },
    )

    if (isEditing) {
        SettingsTextFieldDialog(
            title = title,
            initialValue = value,
            onConfirm = {
                value = it
                isEditing = false
            },
            onDismiss = { isEditing = false },
        )
    }
}

/** An explanation that belongs to the rows around it rather than to any one of them. */
@Composable
fun SettingsNote(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
