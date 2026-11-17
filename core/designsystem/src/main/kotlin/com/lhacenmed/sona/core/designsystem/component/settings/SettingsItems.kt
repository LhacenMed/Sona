@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.core.designsystem.component.settings

import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lhacenmed.sona.core.designsystem.R
import com.lhacenmed.sona.core.designsystem.component.SonaIconButton
import com.lhacenmed.sona.core.designsystem.component.SonaSettingsRow
import com.lhacenmed.sona.core.designsystem.component.settingsDisabledColor
import com.lhacenmed.sona.core.designsystem.component.settingsTitleStyle
import com.lhacenmed.sona.core.designsystem.theme.iconButtonPressShapes
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.component.SonaSwitch

/*
 * A row that only means something while another setting is set some way - black surfaces while the theme
 * is dark, a custom font's file while the font is custom - is never taken off the screen: it stays where it
 * is, not [enabled], faded and inert, and comes back to life the moment it applies. A screen keeps one shape
 * whatever it is set to, so nothing jumps under the finger, and what a setting would unlock can be seen
 * before it is. Every row that can be pressed takes `enabled`, and every one shows it the same way.
 *
 * Every row can be arrived at by its title - a settings search's result opened - see [settingsScrollTarget].
 *
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
    SonaSettingsRow(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = onClick,
        leading = icon?.let { { SettingsLeadingIcon(rememberVectorPainter(it)) } },
        modifier = modifier.settingsScrollTarget(title),
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
    SettingsLeadingContent { Icon(icon, contentDescription = null) }
}

/** [content] at the start of a row, centred in the width every row's leading content takes. */
@Composable
private fun SettingsLeadingContent(content: @Composable () -> Unit) {
    Box(modifier = Modifier.size(LeadingSize), contentAlignment = Alignment.Center) { content() }
}

/**
 * [content] at a row's end, set apart from the row by Seal's short upright line: a control of its own there, which does not
 * do what pressing the row does.
 */
@Composable
private fun SettingsTrailingDivided(content: @Composable () -> Unit) {
    Row(modifier = Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        VerticalDivider(
            modifier = Modifier.height(TrailingDividerHeight).padding(end = TrailingDividerGap),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = TrailingDividerAlpha),
        )
        content()
    }
}

/** Seal's line between a row and the control at its end: this tall, this far from it, and this faint. */
private val TrailingDividerHeight = 32.dp
private val TrailingDividerGap = 8.dp
private const val TrailingDividerAlpha = 0.3f

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
 * A row that does something when pressed - or, while not [enabled], stands faded and inert in its place.
 * While [isBusy] - what it started still under way - it shows so, in place of its [icon] or else at its end, and
 * cannot be pressed again, without fading. A [summary] is for what the title cannot say on its own - a state, a
 * value - and a row whose title says it all has none. [secondaryAction] - its settings, say - stands at its end, apart
 * from the row's own press, as Seal's yt-dlp row has its update settings.
 */
@Composable
fun SettingsActionItem(
    title: String,
    summary: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isBusy: Boolean = false,
    secondaryAction: SettingsSecondaryAction? = null,
) {
    val busyIndicator: @Composable () -> Unit = { LoadingIndicator(modifier = Modifier.size(BusyIndicatorSize)) }
    SonaSettingsRow(
        title = title,
        summary = summary,
        enabled = enabled,
        // Busy, it cannot be pressed again - without fading, as it is not off.
        onClick = onClick.takeUnless { isBusy },
        leading = icon?.let { { if (isBusy) SettingsLeadingContent(busyIndicator) else SettingsLeadingIcon(rememberVectorPainter(it)) } },
        trailing = when {
            secondaryAction != null -> {
                {
                    SettingsTrailingDivided {
                        SonaIconButton(
                            onClick = secondaryAction.onClick,
                            icon = secondaryAction.icon,
                            contentDescription = secondaryAction.label,
                            enabled = enabled,
                        )
                    }
                }
            }
            isBusy && icon == null -> busyIndicator
            else -> null
        },
        modifier = modifier.settingsScrollTarget(title),
    )
}

/** What a row offers at its end, apart from its own press: [label], drawn as [icon], doing [onClick]. */
class SettingsSecondaryAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

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
    SonaSettingsRow(
        title = title,
        summary = summary,
        titleMaxLines = 2,
        summaryMaxLines = 1,
        onClick = { runCatching { uriHandler.openUri(url) } },
        leading = leadingContent,
        trailing = {
            Icon(
                SonaIcons.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = modifier.settingsScrollTarget(title),
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

/** A past search of the settings: pressed, it is searched again; its button forgets it. */
@Composable
fun SettingsRecentSearchItem(
    query: String,
    onClick: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SonaSettingsRow(
        title = query,
        onClick = onClick,
        leading = { SettingsLeadingIcon(rememberVectorPainter(SonaIcons.History)) },
        trailing = { SonaIconButton(onClick = onForget, icon = SonaIcons.Close, contentDescription = stringResource(R.string.settings_recent_search_forget)) },
        modifier = modifier,
    )
}

/** A row that only reports something - a version, a size, a total. Not a control, so not clickable. */
@Composable
fun SettingsInfoItem(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    SonaSettingsRow(title = title, summary = value, modifier = modifier.settingsScrollTarget(title))
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

/**
 * A row that turns a stored setting on or off - or, while not [enabled], still shows which it is, faded and inert.
 * As with [SettingsActionItem], a [summary] only where the title cannot say it on its own.
 */
@Composable
fun SettingsSwitchItem(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SonaSettingsRow(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        leading = icon?.let { { SettingsLeadingIcon(rememberVectorPainter(it)) } },
        trailing = { SonaSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
        modifier = modifier.settingsScrollTarget(title),
    )
}

/**
 * Seal's switch with a divider: a setting that is turned on and off by its switch, and set up by pressing the row - a
 * proxy's address, which parts SponsorBlock cuts. The line between them says the two do different things. While not
 * [enabled] the whole row is faded and inert.
 */
@Composable
fun SettingsSwitchSplitItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SonaSettingsRow(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = onClick,
        leading = icon?.let { { SettingsLeadingIcon(rememberVectorPainter(it)) } },
        trailing = {
            SettingsTrailingDivided {
                SonaSwitch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = title },
                )
            }
        },
        modifier = modifier.settingsScrollTarget(title),
    )
}

/**
 * Seal's switch in a container: the one switch a whole screen hangs on - Use cookies - on a tinted card at its top, the card
 * pressed as one.
 */
@Composable
fun SettingsSwitchContainerItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Switch,
            )
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        Text(
            text = title,
            style = settingsTitleStyle(),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 2,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        SonaSwitch(checked = checked, onCheckedChange = null, interactionSource = interactionSource)
    }
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
 * A row for something the app downloads only for those who want it, and that is then turned on and off like any
 * switch row: a download button while it is not here, its progress while it arrives (pressed, it stops), and a
 * remove button once it is - beside its switch, which works once it is here. A failed download offers itself
 * again. The whole row is the one target, as a switch row is: it downloads while there is nothing to turn on, and
 * turns it on and off once there is.
 *
 * The switch is there whatever the download's state, so the row keeps one shape from the first press to the last. A
 * download that is all there is to it - nothing to turn on once it is here - has none: [checked] null.
 */
@Composable
fun SettingsDownloadItem(
    title: String,
    summary: String,
    download: SettingsDownload,
    checked: Boolean?,
    onCheckedChange: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val isDownloaded = download == SettingsDownload.Done
    val action = when (download) {
        SettingsDownload.Available, SettingsDownload.Failed -> onDownload
        is SettingsDownload.InProgress -> onCancel
        SettingsDownload.Done -> checked?.let { { onCheckedChange(!it) } }
    }
    SonaSettingsRow(
        title = title,
        summary = summary,
        onClick = action,
        leading = icon?.let { { SettingsLeadingIcon(rememberVectorPainter(it)) } },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(DownloadControlSize), contentAlignment = Alignment.Center) {
                    when (download) {
                        SettingsDownload.Available -> IconButton(shapes = iconButtonPressShapes(), onClick = onDownload) {
                            Icon(SonaIcons.Download, contentDescription = stringResource(R.string.settings_download))
                        }
                        SettingsDownload.Failed -> IconButton(shapes = iconButtonPressShapes(), onClick = onDownload) {
                            Icon(SonaIcons.Refresh, contentDescription = stringResource(R.string.settings_download_retry))
                        }
                        is SettingsDownload.InProgress -> {
                            CircularProgressIndicator(progress = { download.progress }, modifier = Modifier.size(DownloadControlSize))
                            IconButton(shapes = iconButtonPressShapes(), onClick = onCancel) {
                                Icon(SonaIcons.Close, contentDescription = stringResource(R.string.settings_download_stop), modifier = Modifier.size(18.dp))
                            }
                        }
                        SettingsDownload.Done -> IconButton(shapes = iconButtonPressShapes(), onClick = onRemove) {
                            Icon(SonaIcons.Delete, contentDescription = stringResource(R.string.settings_download_remove))
                        }
                    }
                }
                if (checked != null) {
                    Spacer(modifier = Modifier.width(DownloadControlSpacing))
                    SonaSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = isDownloaded)
                }
            }
        },
        modifier = modifier.settingsScrollTarget(title),
    )
}

/** The size of a download row's control: a touch target, so the row keeps one height whatever it shows. */
private val DownloadControlSize = 40.dp

/** The gap between a download row's control and its switch. */
private val DownloadControlSpacing = 8.dp

/**
 * A row whose value is one of a fixed set, shown beneath the title and picked from a dialog.
 *
 * The chosen option is the summary, so the row answers "what is this set to" without being opened -
 * which is the whole reason a settings list is worth scrolling. It starts at [selectedIndex]; options go by
 * their names, but for a look's, where [defaultIndex] is Default - see the stored one's.
 */
@Composable
fun SettingsChoiceItem(
    title: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    selectedIndex: Int = 0,
    defaultIndex: Int? = null,
    icon: ImageVector? = null,
) {
    var selected by remember { mutableIntStateOf(selectedIndex) }
    var isChoosing by remember { mutableStateOf(false) }

    SonaSettingsRow(
        title = title,
        summary = if (selected == defaultIndex) settingsDefaultLabel() else options[selected],
        onClick = { isChoosing = true },
        leading = icon?.let { { SettingsLeadingIcon(rememberVectorPainter(it)) } },
        modifier = modifier.settingsScrollTarget(title),
    )

    if (isChoosing) {
        SettingsChoiceDialog(
            title = title,
            options = options,
            selectedIndex = selected,
            defaultIndex = defaultIndex,
            onSelect = {
                selected = it
                isChoosing = false
            },
            onDismiss = { isChoosing = false },
        )
    }
}

/**
 * A row whose stored value is one of a fixed set, picked from a dialog. Each option goes by its own name,
 * which says what the setting does - Shuffle, Stable. Only a look, whose starting option is Sona's own and
 * has no better name, gives [defaultIndex]: that option is then named Default, and listed first. [summary],
 * when given, says what it is set to where the chosen option alone would say too little. While not
 * [enabled], it still says so, faded and inert.
 */
@Composable
fun SettingsChoiceItem(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    defaultIndex: Int? = null,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    var isChoosing by remember { mutableStateOf(false) }

    SonaSettingsRow(
        title = title,
        summary = summary ?: if (selectedIndex == defaultIndex) settingsDefaultLabel() else options[selectedIndex],
        enabled = enabled,
        onClick = { isChoosing = true },
        leading = icon?.let { { SettingsLeadingIcon(rememberVectorPainter(it)) } },
        modifier = modifier.settingsScrollTarget(title),
    )

    if (isChoosing) {
        SettingsChoiceDialog(
            title = title,
            icon = icon,
            options = options,
            selectedIndex = selectedIndex,
            defaultIndex = defaultIndex,
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
 * A row holding a stored number on a continuous range - or, while not [enabled], still showing it, faded
 * and inert: its [icon] and title on one line with the value at its end, in the theme's colour, and the slider
 * under them across the row - all within the edges every row's content keeps, the title where every row's is.
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
    icon: ImageVector? = null,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    var draggedValue by remember(value) { mutableFloatStateOf(value) }
    Column(
        modifier = modifier
            .settingsScrollTarget(title)
            .fillMaxWidth()
            .padding(horizontal = SliderRowPadding, vertical = SliderRowVerticalPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = LeadingSize)) {
            if (icon != null) {
                CompositionLocalProvider(LocalContentColor provides if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else settingsDisabledColor()) {
                    SettingsLeadingIcon(rememberVectorPainter(icon))
                }
                Spacer(modifier = Modifier.width(SliderRowPadding))
            }
            Text(
                text = title,
                style = settingsTitleStyle(),
                color = if (enabled) MaterialTheme.colorScheme.onSurface else settingsDisabledColor(),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatValue(draggedValue),
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.primary else settingsDisabledColor(),
                modifier = Modifier.padding(start = SliderRowPadding),
            )
        }
        Slider(
            value = draggedValue,
            onValueChange = { draggedValue = it },
            onValueChangeFinished = { onValueChangeFinished(draggedValue) },
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** How far a slider row's content stands in from its edges, and from its icon: a list row's, so it lines up with the rest. */
private val SliderRowPadding = 16.dp

/** The room above and below a slider row's content. */
private val SliderRowVerticalPadding = 12.dp

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

    SonaSettingsRow(
        title = title,
        // The value when there is one, the explanation when there is not, so the row is never a
        // bare title with an empty line under it.
        summary = value.ifEmpty { summary },
        onClick = { isEditing = true },
        modifier = modifier.settingsScrollTarget(title),
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
