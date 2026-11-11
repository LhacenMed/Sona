@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.download

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.data.lyrics.LyricsUtils
import com.lhacenmed.sona.core.designsystem.component.InfoSeparator
import com.lhacenmed.sona.core.designsystem.component.SonaCoverImage
import com.lhacenmed.sona.core.designsystem.component.SonaGroupRow
import com.lhacenmed.sona.core.designsystem.component.SonaIconButton
import com.lhacenmed.sona.core.designsystem.component.SonaRowGroup
import com.lhacenmed.sona.core.designsystem.component.SonaValueRow
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaColumnBottomSheet
import com.lhacenmed.sona.core.designsystem.player.formatPlaybackTime
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.feature.tageditor.LyricsPick
import com.lhacenmed.sona.feature.tageditor.MatchLookup
import com.lhacenmed.sona.feature.tageditor.VideoSong
import com.lhacenmed.sona.feature.tageditor.lookup.CatalogueMatch
import com.lhacenmed.sona.feature.tageditor.lyrics.FoundLyrics
import com.lhacenmed.sona.feature.tageditor.lyrics.LyricsTiming
import com.lhacenmed.sona.feature.tageditor.tags.CoverChoice
import com.lhacenmed.sona.feature.tageditor.tags.TagField
import kotlin.math.abs
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons

/** A recording within this much of the video's length is the same recording - the lyrics' own margin. */
private const val SameLengthMarginSeconds = 2L

private val CoverTileSize = 80.dp

/** The pages of the song sheet: the song as it will be saved, and what each of its parts is changed on. */
private enum class SongPage { SONG, CHANGE, LYRICS, TAGS }

/**
 * The song a video's sound is saved as - what the tag editor finds for a track, found before the download: the song, its
 * cover, its lyrics and every tag, each picked unasked and changed on a page of its own, one tap from the song. Done keeps
 * it as the song to save; [onSaveAsFile] keeps the sound as an audio file after all.
 */
@Composable
internal fun SongSheet(song: VideoSong, onSaveAsFile: () -> Unit, onDismiss: () -> Unit) {
    var page by remember { mutableStateOf(if (song.song != null || song.isFinding) SongPage.SONG else SongPage.CHANGE) }
    var isWritingLyrics by remember { mutableStateOf(false) }
    val toSong = { page = SongPage.SONG }
    SonaColumnBottomSheet(
        onDismissRequest = onDismiss,
        header = {
            PageHeader(
                title = stringResource(
                    when (page) {
                        SongPage.SONG -> R.string.download_song
                        SongPage.CHANGE -> R.string.download_song_change_title
                        SongPage.LYRICS -> R.string.download_song_lyrics
                        SongPage.TAGS -> R.string.download_song_all_tags
                    },
                ),
                // Every page goes back to the song; the song, and a search with no song to go back to, to the download.
                onBack = if (page == SongPage.SONG || page == SongPage.CHANGE && song.song == null) null else toSong,
                onClose = onDismiss,
                action = when (page) {
                    SongPage.LYRICS -> ({
                        SonaIconButton(onClick = { isWritingLyrics = true }, icon = SonaIcons.Edit, contentDescription = stringResource(R.string.download_song_lyrics_write))
                    })
                    SongPage.TAGS -> ({
                        TextButton(onClick = song::resetTags, enabled = song.song != null, shapes = buttonPressShapes()) { Text(stringResource(R.string.download_song_reset)) }
                    })
                    else -> null
                },
            )
        },
    ) { sheet ->
        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            when (page) {
                SongPage.SONG -> SongPageContent(song, onOpen = { page = it })
                SongPage.CHANGE -> ChangePageContent(song, onPick = toSong, onSaveAsFile = {
                    onSaveAsFile()
                    sheet.dismiss()
                })
                SongPage.LYRICS -> LyricsPageContent(song, onPick = toSong)
                SongPage.TAGS -> TagsPageContent(song)
            }
        }
        when (page) {
            SongPage.SONG -> DoneButton {
                song.confirm()
                sheet.dismiss()
            }
            SongPage.TAGS -> DoneButton(onClick = toSong)
            else -> Unit
        }
    }
    if (isWritingLyrics) {
        WriteLyricsDialog(
            initial = (song.lyricsPick as? LyricsPick.Written)?.text ?: song.lyrics?.lyrics?.text.orEmpty(),
            onConfirm = { text ->
                song.writeLyrics(text)
                isWritingLyrics = false
                page = SongPage.SONG
            },
            onDismiss = { isWritingLyrics = false },
        )
    }
}

/** A page's title, with the way back to the song beside it - or, on the song itself, the way out - and the page's own [action]. */
@Composable
private fun PageHeader(title: String, onBack: (() -> Unit)?, onClose: () -> Unit, action: (@Composable () -> Unit)?) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SonaIconButton(
            onClick = onBack ?: onClose,
            icon = SonaIcons.ArrowBack,
            contentDescription = stringResource(if (onBack != null) R.string.download_song_back else R.string.download_song_back_to_download),
        )
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
        action?.invoke()
    }
}

/** The sheet's one button at its foot. */
@Composable
private fun DoneButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shapes = buttonPressShapes(),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp).heightIn(min = 56.dp),
    ) {
        Text(stringResource(R.string.download_done), style = MaterialTheme.typography.titleMedium)
    }
}

/** The song as it will be saved: the match, how its length compares, the cover, the lyrics and the tags at a glance. */
@Composable
private fun ColumnScope.SongPageContent(song: VideoSong, onOpen: (SongPage) -> Unit) {
    val match = song.song
    SonaRowGroup(listOf { shapes -> MatchRow(song, match, shapes, onChange = { onOpen(SongPage.CHANGE) }) })
    if (match != null) LengthNote(match, song.durationMs)

    SectionLabel(stringResource(R.string.download_song_cover))
    CoverChoices(song)

    SectionLabel(stringResource(R.string.download_song_lyrics))
    LyricsSummary(song, onClick = { onOpen(SongPage.LYRICS) })

    SectionLabel(stringResource(R.string.download_song_tags))
    val tags = song.tags
    val notSet = stringResource(R.string.download_song_tag_not_set)
    val track = listOfNotNull(
        tags[TagField.TRACK_NUMBER].takeIf { it.isNotBlank() }?.let { number ->
            tags[TagField.TRACK_TOTAL].takeIf { it.isNotBlank() }?.let { stringResource(R.string.download_song_track_of, number, it) } ?: number
        },
        tags[TagField.DISC_NUMBER].takeIf { it.isNotBlank() }?.let { stringResource(R.string.download_song_disc, it) },
    ).joinToString(InfoSeparator)
    val openTags = { onOpen(SongPage.TAGS) }
    SonaRowGroup(
        listOf(
            tagRow(stringResource(R.string.download_song_album), tags[TagField.ALBUM].ifBlank { notSet }, openTags),
            tagRow(stringResource(R.string.download_song_track), track.ifBlank { notSet }, openTags),
            tagRow(stringResource(R.string.download_song_year), listOf(tags[TagField.YEAR], tags[TagField.GENRE]).filter { it.isNotBlank() }.joinToString(InfoSeparator).ifBlank { notSet }, openTags),
            { shapes ->
                SonaValueRow(
                    label = stringResource(R.string.download_song_all_tags),
                    shapes = shapes,
                    icon = SonaIcons.Edit,
                    value = stringResource(R.string.download_song_all_tags_summary),
                    onClick = openTags,
                )
            },
        ),
    )
}

/** The song picked - its cover, name, who sang it on what and where it was found - with Change; or where finding it stands. */
@Composable
private fun MatchRow(song: VideoSong, match: CatalogueMatch?, shapes: ListItemShapes, onChange: () -> Unit) {
    SegmentedListItem(
        onClick = onChange,
        shapes = shapes,
        leadingContent = { SongArt(match?.thumbnailUrl, size = 72) },
        trailingContent = {
            if (song.isFinding) LoadingIndicator(modifier = Modifier.size(40.dp))
            else TextButton(onClick = onChange, shapes = buttonPressShapes()) { Text(stringResource(R.string.download_song_change)) }
        },
        supportingContent = {
            Column {
                Text(
                    text = when {
                        match != null -> match.artist
                        song.isFinding -> stringResource(R.string.download_finding_song)
                        else -> stringResource(R.string.download_song_find_it)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                match?.let { found ->
                    Text(listOf(found.details, found.catalogue).filter { it.isNotBlank() }.joinToString(InfoSeparator), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
    ) {
        Text(
            text = match?.title ?: song.tags[TagField.TITLE].ifBlank { stringResource(R.string.download_song_not_found) },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** How the song's recording compares with the video's - the same length, or not, told before it is kept. */
@Composable
private fun LengthNote(match: CatalogueMatch, videoDurationMs: Long) {
    val seconds = match.durationSeconds ?: return
    if (videoDurationMs <= 0) return
    val video = formatPlaybackTime(videoDurationMs)
    val isSame = abs(seconds * 1000 - videoDurationMs) <= SameLengthMarginSeconds * 1000
    NoteLine(
        icon = if (isSame) SonaIcons.Verified else SonaIcons.Timer,
        text = if (isSame) stringResource(R.string.download_song_same_length, video) else stringResource(R.string.download_song_other_length, formatPlaybackTime(seconds * 1000), video),
        isGood = isSame,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp),
    )
}

/** A short line with its icon - in the primary colour where it says something is right. */
@Composable
private fun NoteLine(icon: ImageVector, text: String, isGood: Boolean, modifier: Modifier = Modifier) {
    val color = if (isGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (isGood) color else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The covers the song can be saved with: each catalogue's, the video's own picture - what the file has unless another is
 * picked - a picture already picked from the gallery, and the gallery. The one picked is ringed and checked.
 */
@Composable
private fun CoverChoices(song: VideoSong) {
    val context = LocalContext.current
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) song.pickPicture(context, uri) }
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        song.catalogueCovers.forEach { match ->
            val choice = CoverChoice.Web(match.coverUrl)
            CoverTile(match.thumbnailUrl.ifBlank { match.coverUrl }, match.catalogue, isSelected = song.cover == choice) { song.chooseCover(choice) }
        }
        CoverTile(song.videoCoverUrl, stringResource(R.string.download_song_cover_video), isSelected = song.cover == CoverChoice.Own) { song.chooseCover(CoverChoice.Own) }
        song.deviceCoverUri?.let { uri ->
            val choice = CoverChoice.Device(uri)
            CoverTile(uri, stringResource(R.string.download_song_cover_picked), isSelected = song.cover == choice) { song.chooseCover(choice) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(CoverTileSize)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .selectable(selected = false, role = Role.Button) { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(SonaIcons.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CoverLabel(stringResource(R.string.download_song_cover_gallery), isSelected = false)
        }
    }
}

@Composable
private fun CoverTile(uri: String?, label: String, isSelected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.padding(4.dp)) {
            SonaCoverImage(
                coverArtUri = uri,
                contentDescription = label,
                cornerRadius = 14.dp,
                modifier = Modifier
                    .size(CoverTileSize)
                    .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                    .clip(shape)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
            )
            if (isSelected) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SonaIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
        CoverLabel(label, isSelected)
    }
}

@Composable
private fun CoverLabel(text: String, isSelected: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.width(CoverTileSize).padding(top = 4.dp),
        textAlign = TextAlign.Center,
    )
}

/** The lyrics the song will be saved with - how closely they follow it, where from, their opening lines - or where finding them stands. */
@Composable
private fun LyricsSummary(song: VideoSong, onClick: () -> Unit) {
    val found = song.lyrics
    val written = (song.lyricsPick as? LyricsPick.Written)?.text
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    found != null -> {
                        TimingTag(found.lyrics.timing)
                        Text(found.source, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    }
                    else -> Text(
                        text = stringResource(
                            when {
                                written != null -> R.string.download_song_lyrics_yours
                                song.lyricsPick == LyricsPick.None -> R.string.download_song_lyrics_none
                                song.isSearchingLyrics -> R.string.download_song_lyrics_searching
                                else -> R.string.download_song_lyrics_none_found
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (found == null && written == null && song.isSearchingLyrics) LoadingIndicator(modifier = Modifier.size(24.dp))
                Icon(SonaIcons.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            (found?.lyrics?.text ?: written)?.let { LyricsPreview(it, lines = 3) }
            found?.let { LyricsLength(it, song.durationMs) }
        }
    }
}

/** How closely lyrics follow the song: word by word, line by line, or not at all. */
@Composable
private fun TimingTag(timing: LyricsTiming) {
    val isSynced = timing != LyricsTiming.NONE
    Text(
        text = stringResource(
            when (timing) {
                LyricsTiming.WORD -> R.string.download_song_lyrics_word_synced
                LyricsTiming.LINE -> R.string.download_song_lyrics_synced
                LyricsTiming.NONE -> R.string.download_song_lyrics_plain
            },
        ),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (isSynced) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isSynced) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Lyrics' opening [lines], as they are read rather than as they are timed. */
@Composable
private fun LyricsPreview(text: String, lines: Int) {
    val preview = remember(text) { LyricsUtils.displayLyricsText(text).lineSequence().filter { it.isNotBlank() }.take(lines).joinToString("\n") }
    Text(preview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = lines, overflow = TextOverflow.Ellipsis)
}

/** The length of the recording lyrics were timed to beside the video's - "4:12 Â· same length", "3:58 Â· 14 s shorter, may drift". */
@Composable
private fun LyricsLength(found: FoundLyrics, videoDurationMs: Long) {
    val durationMs = found.durationMs
    val difference = found.durationDifferenceMs(videoDurationMs)
    val fits = found.fits(videoDurationMs)
    val text = when {
        found.lyrics.timing == LyricsTiming.NONE -> stringResource(R.string.download_song_lyrics_no_timing)
        durationMs == null -> stringResource(R.string.download_song_lyrics_unknown_length)
        videoDurationMs <= 0 || difference == null -> formatPlaybackTime(durationMs)
        fits -> stringResource(R.string.download_song_lyrics_same, formatPlaybackTime(durationMs))
        else -> stringResource(
            if (difference > 0) R.string.download_song_lyrics_longer else R.string.download_song_lyrics_shorter,
            formatPlaybackTime(durationMs),
            gapText(abs(difference)),
        )
    }
    NoteLine(
        icon = when {
            found.lyrics.timing == LyricsTiming.NONE -> SonaIcons.Notes
            fits || durationMs == null -> SonaIcons.Timer
            else -> SonaIcons.Warning
        },
        text = text,
        isGood = fits,
    )
}

/** A gap between two lengths: "14 s", or "1:05" from a minute on. */
@Composable
private fun gapText(gapMs: Long): String {
    val seconds = (gapMs + 500) / 1000
    return if (seconds < 60) stringResource(R.string.download_song_seconds, seconds.toInt()) else formatPlaybackTime(gapMs)
}

/** A tag at a glance: its name, then what it is - opening every tag. */
private fun tagRow(name: String, value: String, onClick: () -> Unit): SonaGroupRow = { shapes ->
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        leadingContent = { Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp)) },
    ) {
        Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Another song: the search filled with the video's title as catalogues name it, narrowed to one catalogue with a tap, each
 * result's length beside the video's so the right recording is told at a glance; a tap takes it - tags, cover and lyrics.
 */
@Composable
private fun ColumnScope.ChangePageContent(song: VideoSong, onPick: () -> Unit, onSaveAsFile: () -> Unit) {
    var text by remember { mutableStateOf(song.queryTitle.takeIf { song.matchLookup is MatchLookup.Found && it.isNotBlank() }?.let { listOf(song.queryArtist, it).filter(String::isNotBlank).joinToString(" ") } ?: song.searchText) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(stringResource(R.string.download_search_songs)) },
        leadingIcon = { Icon(SonaIcons.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) SonaIconButton(onClick = { text = "" }, icon = SonaIcons.Close, contentDescription = stringResource(R.string.download_clear_search))
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { song.searchFor(text) }),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceChip(stringResource(R.string.download_song_all), selected = song.catalogueFilter == null) { song.filterCatalogue(null) }
            song.catalogueNames.forEach { name -> SourceChip(name, selected = song.catalogueFilter == name) { song.filterCatalogue(name) } }
        }
        if (song.durationMs > 0) {
            Text(
                stringResource(R.string.download_song_video_length, formatPlaybackTime(song.durationMs)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
    val pick = { match: CatalogueMatch ->
        song.pick(match)
        onPick()
    }
    val matches = song.matches
    when {
        song.matchLookup == MatchLookup.Searching -> Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
        matches.isEmpty() -> Text(
            text = stringResource(if (song.matchLookup == MatchLookup.Unreachable) R.string.download_find_song_unreachable else R.string.download_find_song_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
        )
        else -> {
            SectionLabel(stringResource(R.string.download_find_song_best))
            SonaRowGroup(listOf(resultRow(song, matches.first(), pick)))
            if (matches.size > 1) {
                SectionLabel(stringResource(R.string.download_find_song_others))
                SonaRowGroup(matches.drop(1).map { resultRow(song, it, pick) })
            }
        }
    }
    SonaRowGroup(
        listOf { shapes ->
            SonaValueRow(label = stringResource(R.string.download_save_as_file_instead), shapes = shapes, icon = SonaIcons.AudioFile, trailing = null, onClick = onSaveAsFile)
        },
        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
    )
}

@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) ({ Icon(SonaIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }) else null,
    )
}

/** A song a catalogue found: its cover, its name, who sang it on what; its length and catalogue - picked out where it is the song. */
private fun resultRow(song: VideoSong, match: CatalogueMatch, onPick: (CatalogueMatch) -> Unit): SonaGroupRow = { shapes ->
    val isPicked = song.song != null && song.completed(match) == song.song
    SegmentedListItem(
        onClick = { onPick(match) },
        shapes = shapes,
        colors = if (isPicked) ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ListItemDefaults.segmentedColors(),
        leadingContent = { SongArt(match.thumbnailUrl, size = 56) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                match.durationSeconds?.let { Text(formatPlaybackTime(it * 1000), style = MaterialTheme.typography.bodyMedium) }
                Text(match.catalogue, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        supportingContent = { Text(listOf(match.artist, match.details).filter { it.isNotBlank() }.joinToString(InfoSeparator), maxLines = 1, overflow = TextOverflow.Ellipsis) },
    ) {
        Text(match.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Every set of lyrics the sources found, narrowed to one source with a tap: how closely each follows the song and whether
 * it was timed to a recording of the video's length - those that fit first - and its opening lines. Or none at all.
 */
@Composable
private fun ColumnScope.LyricsPageContent(song: VideoSong, onPick: () -> Unit) {
    val counts = song.lyricsResults.groupingBy { it.source }.eachCount()
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SourceChip(stringResource(R.string.download_song_counted, stringResource(R.string.download_song_all), song.lyricsResults.size), selected = song.lyricsFilter == null) {
            song.filterLyrics(null)
        }
        song.lyricsSourceNames.filter { it in counts }.forEach { source ->
            SourceChip(stringResource(R.string.download_song_counted, source, counts.getValue(source)), selected = song.lyricsFilter == source) { song.filterLyrics(source) }
        }
    }
    Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        song.visibleLyrics.forEach { found ->
            val isPicked = song.lyrics == found
            Card(
                onClick = {
                    song.chooseLyrics(found)
                    onPick()
                },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = if (isPicked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimingTag(found.lyrics.timing)
                        Text(found.source, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        if (isPicked) Icon(SonaIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    LyricsLength(found, song.durationMs)
                    LyricsPreview(found.lyrics.text, lines = 2)
                }
            }
        }
        if (song.isSearchingLyrics || song.lyricsResults.isEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (song.isSearchingLyrics) LoadingIndicator(modifier = Modifier.size(24.dp))
                Text(
                    stringResource(if (song.isSearchingLyrics) R.string.download_song_lyrics_searching else R.string.download_song_lyrics_none_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SonaRowGroup(
            listOf { shapes ->
                SonaValueRow(
                    label = stringResource(R.string.download_song_lyrics_skip),
                    shapes = shapes,
                    icon = SonaIcons.Block,
                    trailing = if (song.lyricsPick == LyricsPick.None) ({ Icon(SonaIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }) else null,
                    onClick = {
                        song.skipLyrics()
                        onPick()
                    },
                )
            },
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}

/** Every tag the song is saved with, from the match: the short ones side by side, each one changed from it marked as such. */
@Composable
private fun TagsPageContent(song: VideoSong) {
    @Composable
    fun Field(field: TagField, label: String, modifier: Modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = song.tags[field],
            onValueChange = { song.setField(field, it) },
            label = { Text(if (song.isEdited(field)) stringResource(R.string.download_song_edited, label) else label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (field.isNumeric) KeyboardType.Number else KeyboardType.Text, imeAction = ImeAction.Next),
            modifier = modifier,
        )
    }
    Column(modifier = Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(TagField.TITLE, TagField.ARTIST, TagField.ALBUM, TagField.ALBUM_ARTIST).forEach { Field(it, it.label) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(TagField.YEAR, TagField.YEAR.label, Modifier.weight(1f))
            Field(TagField.GENRE, TagField.GENRE.label, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(TagField.TRACK_NUMBER, stringResource(R.string.download_song_track), Modifier.weight(1f))
            Field(TagField.TRACK_TOTAL, stringResource(R.string.download_song_track_total), Modifier.weight(1f))
            Field(TagField.DISC_NUMBER, stringResource(R.string.download_song_disc_label), Modifier.weight(1f))
        }
        listOf(TagField.COMPOSER, TagField.COMMENT).forEach { Field(it, it.label) }
    }
}

/** Lyrics written or pasted by the user, starting from those picked - none, where left blank. */
@Composable
private fun WriteLyricsDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val cancelLabel = stringResource(R.string.download_cancel)
    val useLabel = stringResource(R.string.download_song_lyrics_use)
    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.download_song_lyrics_write),
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = useLabel, onClick = { onConfirm(text) })
        },
    ) {
        OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 6, maxLines = 12, modifier = Modifier.fillMaxWidth())
    }
}

/** A song's cover, [size] dp square - a shimmer until there is one to show. */
@Composable
internal fun SongArt(url: String?, size: Int) {
    SonaCoverImage(coverArtUri = url?.ifBlank { null }, contentDescription = null, cornerRadius = 8.dp, modifier = Modifier.size(size.dp))
}

/** The picture the song will be saved with: the one picked, or the video's own. */
internal val VideoSong.coverUri: String?
    get() = when (val choice = cover) {
        CoverChoice.Own -> videoCoverUrl
        is CoverChoice.Web -> choice.url
        is CoverChoice.Device -> choice.uri
    }
