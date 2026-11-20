package com.lhacenmed.sona.feature.tageditor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.lhacenmed.sona.core.designsystem.component.rememberSyncedTextFieldState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.CoverArtDefaults
import com.lhacenmed.sona.core.designsystem.component.SonaCoverImage
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.section.ColumnSection
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.feature.tageditor.R
import com.lhacenmed.sona.feature.tageditor.tags.CoverChoice
import com.lhacenmed.sona.feature.tageditor.tags.TagField
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons

/**
 * Everything [draft] can be edited with, one section after another - the track as it will be saved, with its play
 * button, the search, the best match, every tag, every cover, every set of lyrics and the other matches.
 * [onEditLyrics] opens the lyrics editor over them.
 */
@Composable
internal fun ColumnScope.TrackTagDraftSections(
    draft: TrackTagDraft,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    onEditLyrics: () -> Unit,
) {
    val track = draft.track
    val tags = draft.tags
    val cover = draft.cover
    val best = draft.matches.firstOrNull()
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) draft.pickDeviceCover(uri.toString())
    }

    // The track as it will be saved: the cover chosen, and the names as they stand.
    SonaCoverImage(
        coverArtUri = cover.uriOr(track.coverArtUri),
        contentDescription = null,
        cornerRadius = CoverArtDefaults.DetailHeaderCornerRadius,
        modifier = Modifier.padding(16.dp).size(CoverArtDefaults.DetailHeaderSize),
    )
    HeaderText(tags[TagField.TITLE].ifBlank { track.title }, MaterialTheme.typography.titleLargeEmphasized)
    HeaderText(tags[TagField.ARTIST].ifBlank { track.artist }, MaterialTheme.typography.bodyLarge)
    if (track.isVideo) {
        VideoDraftSections(draft, onPickFromGallery = { galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
        return
    }
    // Under the names, as a collection's Play is under its header: the song is heard while its tags are told apart.
    FilledTonalButton(onClick = onTogglePlayback, shapes = buttonPressShapes(), modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
        Icon(
            imageVector = if (isPlaying) SonaIcons.Pause else SonaIcons.Play,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Text(stringResource(if (isPlaying) R.string.tag_editor_pause else R.string.tag_editor_play), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
    }

    ColumnSection(title = stringResource(R.string.tag_editor_search)) {
        SearchFields(
            artist = draft.queryArtist,
            title = draft.queryTitle,
            onArtistChange = { draft.queryArtist = it },
            onTitleChange = { draft.queryTitle = it },
            onSearch = draft::search,
        )
    }

    HorizontalDivider()
    ColumnSection(title = stringResource(R.string.tag_editor_best_match)) {
        SourceFilter(sources = draft.catalogueNames, selected = draft.catalogueFilter, onSelect = draft::filterCatalogue)
        BestMatchCard(
            lookup = draft.matchLookup,
            best = best?.let(draft::completed),
            isApplied = best != null && draft.appliedMatch == best,
            onApply = { best?.let(draft::apply) },
            onRetry = draft::search,
        )
    }

    HorizontalDivider()
    ColumnSection(title = stringResource(R.string.tag_editor_tags)) {
        TagFields(tags = tags, onChange = draft::setField)
    }

    HorizontalDivider()
    ColumnSection(title = stringResource(R.string.tag_editor_covers)) {
        CoverChoices(
            ownCoverUri = track.coverArtUri,
            deviceCoverUri = draft.deviceCoverUri,
            matchCovers = draft.matches.filter { it.coverUrl.isNotBlank() }.distinctBy { it.coverUrl },
            selected = cover,
            onPickFromGallery = { galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onSelect = draft::chooseCover,
        )
    }

    HorizontalDivider()
    ColumnSection(
        title = stringResource(R.string.tag_editor_lyrics),
        actions = listOf(TopBarAction(label = stringResource(R.string.tag_editor_edit_lyrics), icon = SonaIcons.Edit, onClick = onEditLyrics)),
    ) {
        SourceFilter(
            sources = draft.lyricsSourceNames,
            selected = draft.lyricsFilter,
            onSelect = draft::filterLyrics,
            counts = draft.lyricsResults.groupingBy { it.source }.eachCount(),
        )
        draft.visibleLyrics.forEach { found ->
            LyricsResultCard(
                found = found,
                trackDurationMs = track.durationMs,
                isApplied = tags[TagField.LYRICS] == found.lyrics.text,
                onApply = { draft.applyLyrics(found) },
            )
        }
        LyricsSearchStatus(isSearching = draft.isSearchingLyrics, hasResults = draft.visibleLyrics.isNotEmpty())
    }

    val others = draft.matches.drop(1)
    if (others.isNotEmpty()) {
        HorizontalDivider()
        ColumnSection(title = stringResource(R.string.tag_editor_other_matches)) {
            others.forEach { match ->
                MatchRow(
                    match = match,
                    isApplying = draft.applyingMatch == match,
                    isApplied = draft.appliedMatch == match,
                    onApply = { draft.apply(match) },
                )
            }
        }
    }
}

/**
 * A video's own: the tags a video carries - see [TagField.describesVideos] - its thumbnail, from its own or the gallery, and
 * the link it came from, which its details are read again from. Nothing of a song's - no catalogue, no lyrics.
 */
@Composable
private fun ColumnScope.VideoDraftSections(draft: TrackTagDraft, onPickFromGallery: () -> Unit) {
    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider()
    ColumnSection(title = stringResource(R.string.tag_editor_tags)) {
        TagFields(tags = draft.tags, onChange = draft::setField, fields = VideoFields)
    }

    HorizontalDivider()
    ColumnSection(title = stringResource(R.string.tag_editor_thumbnail)) {
        CoverChoices(
            ownCoverUri = draft.track.coverArtUri,
            deviceCoverUri = draft.deviceCoverUri,
            matchCovers = emptyList(),
            selected = draft.cover,
            onPickFromGallery = onPickFromGallery,
            onSelect = draft::chooseCover,
        )
    }

    HorizontalDivider()
    ColumnSection(title = stringResource(R.string.tag_editor_link)) {
        val link = draft.link.trim()
        OutlinedTextField(
            state = rememberSyncedTextFieldState(draft.link) { draft.link = it },
            label = { Text(stringResource(R.string.tag_editor_link_label)) },
            supportingText = { Text(stringResource(if (link.isEmpty() || isWebAddress(link)) R.string.tag_editor_link_summary else R.string.tag_editor_link_invalid)) },
            isError = link.isNotEmpty() && !isWebAddress(link),
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** The tags a video carries, in the order they are laid out. */
private val VideoFields = TagField.entries.filter(TagField::describesVideos)

/** The picture this cover choice stands for - [own], the file's, for the file's own. */
internal fun CoverChoice.uriOr(own: String?): String? = when (this) {
    CoverChoice.Own -> own
    is CoverChoice.Web -> url
    is CoverChoice.Device -> uri
}

@Composable
private fun HeaderText(text: String, style: TextStyle) {
    Text(
        text = text,
        style = style,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}
