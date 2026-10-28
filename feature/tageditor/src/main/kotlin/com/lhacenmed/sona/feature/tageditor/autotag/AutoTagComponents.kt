@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.tageditor.autotag

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.SonaChoiceRow
import com.lhacenmed.sona.core.designsystem.component.SonaCoverArt
import com.lhacenmed.sona.core.designsystem.component.SonaFolderCover
import com.lhacenmed.sona.core.designsystem.component.SonaListRow
import com.lhacenmed.sona.core.designsystem.component.section.SectionMotion
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.feature.tageditor.uriOr
import com.lhacenmed.sona.feature.tageditor.tags.TagField

private val ContentPadding = SonaComponentStyle.ContentHorizontalPadding

/** The run's tracks, as many as its folders hold - pressed, which folders those are. */
@Composable
internal fun TracksRow(trackCount: Int, folders: List<Pair<Folder, Int>>, leftOutFolders: Set<String>, onClick: () -> Unit) {
    val kept = folders.count { (folder, _) -> folder.path !in leftOutFolders }
    SonaListRow(
        title = tracksLabel(trackCount),
        subtitle = if (kept == folders.size) "All ${foldersLabel(folders.size)}" else "$kept of ${foldersLabel(folders.size)}",
        selection = null,
        selectionKey = null,
        onClick = onClick,
        onOpenOptions = null,
        cover = { SonaFolderCover(coverArtUris = folders.flatMap { it.first.coverArtUris }.distinct(), seed = 0) },
    )
}

/** What the run finds - tags, a cover, lyrics - each pressed in or out. */
@Composable
internal fun FindChoices(choices: AutoTagChoices, onChange: (AutoTagChoices) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = ContentPadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(SonaComponentStyle.ItemSpacing),
    ) {
        FilterChip(selected = choices.findsTags, onClick = { onChange(choices.copy(findsTags = !choices.findsTags)) }, label = { Text("Tags") })
        FilterChip(selected = choices.findsCover, onClick = { onChange(choices.copy(findsCover = !choices.findsCover)) }, label = { Text("Cover") })
        FilterChip(selected = choices.findsLyrics, onClick = { onChange(choices.copy(findsLyrics = !choices.findsLyrics)) }, label = { Text("Lyrics") })
    }
}

/**
 * What only some want to change, folded away until pressed open: whether what the file already holds is replaced
 * by what is found, or kept with only its blanks filled.
 */
@Composable
internal fun AdvancedChoices(choices: AutoTagChoices, onChange: (AutoTagChoices) -> Unit) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = ContentPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Advanced", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        }
        val fade = SectionMotion.fade()
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(SectionMotion.size, expandFrom = Alignment.Top) + fadeIn(fade),
            exit = shrinkVertically(SectionMotion.size, shrinkTowards = Alignment.Top) + fadeOut(fade),
        ) {
            Column {
                Text(
                    "Where a track already has a value",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ContentPadding),
                )
                SonaChoiceRow(
                    label = "Replace it with the match's",
                    selected = !choices.fillsBlanksOnly,
                    onClick = { onChange(choices.copy(fillsBlanksOnly = false)) },
                )
                SonaChoiceRow(
                    label = "Keep it, and fill only what is blank",
                    selected = choices.fillsBlanksOnly,
                    onClick = { onChange(choices.copy(fillsBlanksOnly = true)) },
                )
            }
        }
    }
}

/** The status slot's size - a list cover's, so the panel's text starts where every row's below it does. */
private val StatusSize = 40.dp

/**
 * How far the run is, pinned under the top bar and lifting with it as one section - see [AutoTagScreen]: what it is
 * doing, how far along, and the one thing to do about it - stop looking up, or finish with the run. The status, the
 * text and the button keep their places in every state; the progress bar under them folds away once the run waits
 * on the user, and back as it works again, the list moving with it as a section's fold moves it.
 */
@Composable
internal fun RunProgress(session: AutoTagSession, onStop: () -> Unit, onFinish: () -> Unit) {
    val phase = session.phase
    val isWorking = phase != AutoTagPhase.REVIEWING
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ContentPadding, end = ContentPadding, top = 4.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(StatusSize), contentAlignment = Alignment.Center) {
                if (isWorking) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(28.dp))
                } else {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    text = when (phase) {
                        AutoTagPhase.MATCHING -> "Looking up tracks"
                        AutoTagPhase.SAVING -> "Saving tags"
                        AutoTagPhase.REVIEWING -> "Ready to review"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when (phase) {
                        AutoTagPhase.REVIEWING -> "${session.pickedItems.size} of ${tracksLabel(session.changedTrackIds.size)} with changes picked"
                        else -> "${session.done} of ${tracksLabel(session.total)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalButton(
                onClick = if (phase == AutoTagPhase.MATCHING) onStop else onFinish,
                // Saving is never left halfway, so there is nothing to press while it runs.
                enabled = phase != AutoTagPhase.SAVING,
                shapes = buttonPressShapes(),
            ) {
                Text(if (phase == AutoTagPhase.REVIEWING) "Finish" else "Stop")
            }
        }
        val fade = SectionMotion.fade()
        AnimatedVisibility(
            visible = isWorking,
            enter = expandVertically(SectionMotion.size, expandFrom = Alignment.Top) + fadeIn(fade),
            exit = shrinkVertically(SectionMotion.size, shrinkTowards = Alignment.Top) + fadeOut(fade),
        ) {
            // Glides to each track done rather than stepping, at Material's own pace for a progress indicator.
            val progress by animateFloatAsState(
                targetValue = if (session.total == 0) 0f else session.done.toFloat() / session.total,
                animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
                label = "autoTagProgress",
            )
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        }
    }
}

/**
 * One track of the run, as saving would leave it - its cover and title - over what saving would change. Pressed, it
 * is picked or unpicked; its pen opens it in the tag editor, on everything found for it.
 */
@Composable
internal fun AutoTagRow(item: AutoTagItem, picks: SelectionState, onOpen: () -> Unit) {
    val draft = item.draft
    val trackId = item.track.id
    SonaListRow(
        title = draft?.tags?.get(TagField.TITLE)?.ifBlank { null } ?: item.track.title,
        subtitle = item.summary,
        selection = picks,
        selectionKey = trackId.takeIf { item.hasChanges },
        onClick = { if (item.hasChanges) picks.toggle(trackId) else onOpen() },
        onOpenOptions = onOpen,
        optionsIcon = SonaIcons.Edit,
        optionsLabel = "Edit tags",
        cover = { isSelected ->
            SonaCoverArt(
                coverArtUri = draft?.cover?.uriOr(item.track.coverArtUri) ?: item.track.coverArtUri,
                contentDescription = null,
                isSelected = isSelected,
            )
        },
    )
}

/** "1 folder", "18 folders". */
private fun foldersLabel(count: Int): String = if (count == 1) "1 folder" else "$count folders"
