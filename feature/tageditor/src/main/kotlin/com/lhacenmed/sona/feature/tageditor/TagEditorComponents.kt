@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.tageditor

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.data.lyrics.LyricsUtils
import com.lhacenmed.sona.core.designsystem.component.CoverArtDefaults
import com.lhacenmed.sona.core.designsystem.component.SonaCoverImage
import com.lhacenmed.sona.core.designsystem.component.shape
import com.lhacenmed.sona.core.designsystem.theme.LocalCoverStyle
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.feature.tageditor.lookup.CatalogueMatch
import com.lhacenmed.sona.feature.tageditor.lyrics.FoundLyrics
import com.lhacenmed.sona.feature.tageditor.lyrics.LyricsTiming
import com.lhacenmed.sona.feature.tageditor.tags.CoverChoice
import com.lhacenmed.sona.feature.tageditor.tags.TagField
import com.lhacenmed.sona.feature.tageditor.tags.TrackTags

/** The best match's card keeps this height whatever it holds, so nothing under it moves as the lookup ends. */
private val MatchCardMinHeight = 112.dp
private val MatchCoverSize = 88.dp
private val CoverChoiceSize = 112.dp
private val SelectedRingWidth = 3.dp

/** How much of a set of lyrics its card shows - enough to tell one from another. */
private const val LyricsPreviewLines = 4

private val ContentPadding = SonaComponentStyle.ContentHorizontalPadding

/** What is looked up, as it stands - the track's likeliest names to begin with - and the search that runs it again. */
@Composable
internal fun SearchFields(
    artist: String,
    title: String,
    onArtistChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = ContentPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = artist,
            onValueChange = onArtistChange,
            label = { Text("Artist") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onSearch,
            enabled = title.isNotBlank(),
            shapes = buttonPressShapes(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text("Search tags and lyrics", modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
        }
    }
}

/**
 * Which source what was found is narrowed to - all of them, or one - with how many each found where [counts]
 * says. Narrowing asks nothing again: it only chooses among what is already here.
 */
@Composable
internal fun SourceFilter(
    sources: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    counts: Map<String, Int>? = null,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = ContentPadding),
        horizontalArrangement = Arrangement.spacedBy(SonaComponentStyle.ItemSpacing),
    ) {
        item(key = "all") {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        }
        items(sources, key = { it }) { source ->
            val count = counts?.get(source)
            FilterChip(
                selected = selected == source,
                onClick = { onSelect(source) },
                label = { Text(if (count != null) "$source · $count" else source) },
            )
        }
    }
}

/**
 * The best match - AutomaTag's "best match" card: while the lookup runs, what it is doing; once it ends, the
 * song found, with its cover, names, album and year, and where it came from. Pressed, it applies every tag it
 * has and its cover; its chevron lays out which tags those are before that.
 */
@Composable
internal fun BestMatchCard(
    lookup: MatchLookup,
    best: CatalogueMatch?,
    isApplied: Boolean,
    onApply: (CatalogueMatch) -> Unit,
    onRetry: () -> Unit,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    Card(
        onClick = { best?.let(onApply) },
        enabled = best != null,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ContentPadding, vertical = 8.dp)
            .heightIn(min = MatchCardMinHeight)
            .animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec()),
    ) {
        when {
            best != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SonaCoverImage(
                        coverArtUri = best.thumbnailUrl.ifBlank { null },
                        contentDescription = null,
                        cornerRadius = SonaComponentStyle.CornerRadius,
                        modifier = Modifier.padding(12.dp).size(MatchCoverSize),
                    )
                    Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
                        Text(best.title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(best.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (best.details.isNotBlank()) {
                            Text(
                                best.details,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            if (isApplied) "Applied" else "From ${best.catalogue} · press to apply",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (isExpanded) "Hide its tags" else "Show its tags",
                        )
                    }
                }
                if (isExpanded) MatchFields(best.tags)
            }
            lookup == MatchLookup.Searching -> CardMessage("Looking up tags…") {
                CircularWavyProgressIndicator(modifier = Modifier.size(32.dp))
            }
            lookup == MatchLookup.Unreachable -> CardMessage(
                text = "The catalogues could not be reached.",
                action = { TextButton(onClick = onRetry) { Text("Retry") } },
            ) {
                Icon(Icons.Filled.CloudOff, contentDescription = null)
            }
            else -> CardMessage("No match found. Change the search, or edit the tags below.") {
                Icon(Icons.Filled.SearchOff, contentDescription = null)
            }
        }
    }
}

/** Every field, to be changed by hand - AutomaTag's "metadata fields". */
@Composable
internal fun TagFields(tags: TrackTags, onChange: (TagField, String) -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = ContentPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TagField.entries.forEach { field ->
            OutlinedTextField(
                value = tags[field],
                onValueChange = { onChange(field, it) },
                label = { Text(field.label) },
                singleLine = !field.isMultiline,
                minLines = if (field.isMultiline) 3 else 1,
                maxLines = if (field.isMultiline) 10 else 1,
                keyboardOptions = KeyboardOptions(keyboardType = if (field.isNumeric) KeyboardType.Number else KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The covers the track can take - AutomaTag's "cover images": a picture from the gallery, always first, so
 * there is one to choose whatever the lookup found; then the file's own, a picture already picked, and every
 * match's. The one it will be saved with is ringed.
 */
@Composable
internal fun CoverChoices(
    ownCoverUri: String?,
    deviceCoverUri: String?,
    matchCovers: List<CatalogueMatch>,
    selected: CoverChoice,
    onPickFromGallery: () -> Unit,
    onSelect: (CoverChoice) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = ContentPadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(SonaComponentStyle.ItemSpacing),
    ) {
        item(key = "gallery") { GalleryTile(onClick = onPickFromGallery) }
        item(key = "own") {
            CoverChoice(uri = ownCoverUri, label = "Current", isSelected = selected == CoverChoice.Own) { onSelect(CoverChoice.Own) }
        }
        if (deviceCoverUri != null) {
            item(key = "device") {
                val choice = CoverChoice.Device(deviceCoverUri)
                CoverChoice(uri = deviceCoverUri, label = "From gallery", isSelected = selected == choice) { onSelect(choice) }
            }
        }
        items(matchCovers, key = { it.coverUrl }) { match ->
            val choice = CoverChoice.Web(match.coverUrl)
            CoverChoice(uri = match.thumbnailUrl, label = match.catalogue, isSelected = selected == choice) { onSelect(choice) }
        }
    }
}

@Composable
private fun GalleryTile(onClick: () -> Unit) {
    val shape = LocalCoverStyle.current.shape(CoverArtDefaults.ListCornerRadius)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(
            modifier = Modifier
                .size(CoverChoiceSize)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
            Text(
                "Add from gallery",
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        // The same line under it as under every cover, so the row keeps one height.
        Text(" ", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CoverChoice(uri: String?, label: String, isSelected: Boolean, onClick: () -> Unit) {
    val shape = LocalCoverStyle.current.shape(CoverArtDefaults.ListCornerRadius)
    val ring = if (isSelected) Modifier.border(SelectedRingWidth, MaterialTheme.colorScheme.primary, shape) else Modifier
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SonaCoverImage(
            coverArtUri = uri,
            contentDescription = label,
            cornerRadius = CoverArtDefaults.ListCornerRadius,
            modifier = Modifier
                .size(CoverChoiceSize)
                .then(ring)
                .clickable(onClick = onClick),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One set of lyrics a source found: where from, how closely it follows the song, the length of the recording it
 * was timed to beside the track's own [trackDurationMs], and its opening lines. Pressed, it becomes the track's
 * lyrics.
 */
@Composable
internal fun LyricsResultCard(found: FoundLyrics, trackDurationMs: Long, isApplied: Boolean, onApply: () -> Unit) {
    val preview = remember(found) {
        LyricsUtils.displayLyricsText(found.lyrics.text).lineSequence().take(LyricsPreviewLines).joinToString("\n")
    }
    Card(
        onClick = onApply,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().padding(horizontal = ContentPadding, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${found.source} · ${found.lyrics.timing.label}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (isApplied) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Applied", tint = MaterialTheme.colorScheme.primary)
                }
            }
            LyricsDuration(found = found, trackDurationMs = trackDurationMs)
            Text(
                preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = LyricsPreviewLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The length of the recording lyrics were timed to, and how it compares with the track's - "3:48 · Same length",
 * "3:21 · 27s shorter" - picked out where they fit, as synced lyrics only follow the recording they were timed to.
 */
@Composable
private fun LyricsDuration(found: FoundLyrics, trackDurationMs: Long) {
    val durationMs = found.durationMs
    val difference = found.durationDifferenceMs(trackDurationMs)
    val fits = found.fits(trackDurationMs)
    val text = when {
        durationMs == null -> "Length not given"
        trackDurationMs <= 0 || difference == null -> formatDuration(durationMs)
        fits -> "${formatDuration(durationMs)} · Same length"
        difference > 0 -> "${formatDuration(durationMs)} · ${formatGap(difference)} longer"
        else -> "${formatDuration(durationMs)} · ${formatGap(-difference)} shorter"
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Timer,
            contentDescription = null,
            tint = if (fits) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (fits) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A length as a clock reads it: "3:48", or "1:02:05" past an hour. */
internal fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs + 500) / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds / 60 % 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/** A gap between two lengths: "27s", or "1:05" from a minute on. */
private fun formatGap(gapMs: Long): String {
    val seconds = (gapMs + 500) / 1000
    return if (seconds < 60) "${seconds}s" else formatDuration(gapMs)
}

/** The line under the lyrics while sources are still answering, or when none had any - so the list never just ends. */
@Composable
internal fun LyricsSearchStatus(isSearching: Boolean, hasResults: Boolean) {
    if (!isSearching && hasResults) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ContentPadding, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (isSearching) {
                CircularWavyProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Filled.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            if (isSearching) "Looking for lyrics…" else "No lyrics found",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Another match - AutomaTag's "matches found": pressed, it is completed and applied, as the best one is. */
@Composable
internal fun MatchRow(match: CatalogueMatch, isApplying: Boolean, isApplied: Boolean, onApply: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isApplying, onClick = onApply)
            .padding(horizontal = ContentPadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SonaCoverImage(
            coverArtUri = match.thumbnailUrl.ifBlank { null },
            contentDescription = null,
            cornerRadius = CoverArtDefaults.ListCornerRadius,
            modifier = Modifier.size(CoverArtDefaults.ListSize),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(match.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(match.artist, match.details, match.catalogue).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                isApplying -> CircularWavyProgressIndicator(modifier = Modifier.size(20.dp))
                isApplied -> Icon(Icons.Filled.CheckCircle, contentDescription = "Applied", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** A line of what the file is: its format, bitrate or path. */
@Composable
internal fun FileFact(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = ContentPadding, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The card while it has no match to show: what is happening, and what can be done about it. */
@Composable
private fun CardMessage(text: String, action: (@Composable () -> Unit)? = null, icon: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = MatchCardMinHeight).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) { icon() }
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** The tags a match fills in - AutomaTag's "this match is made of". */
@Composable
private fun MatchFields(tags: TrackTags) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.filledFields.forEach { field -> MatchField(field.icon, tags[field], field.label) }
    }
}

@Composable
private fun MatchField(icon: ImageVector, value: String, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val LyricsTiming.label: String
    get() = when (this) {
        LyricsTiming.WORD -> "Word-synced"
        LyricsTiming.LINE -> "Synced"
        LyricsTiming.NONE -> "Plain"
    }
