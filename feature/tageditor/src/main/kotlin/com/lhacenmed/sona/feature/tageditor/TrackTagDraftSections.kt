package com.lhacenmed.sona.feature.tageditor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.CoverArtDefaults
import com.lhacenmed.sona.core.designsystem.component.SonaCoverImage
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.section.ColumnSection
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.feature.tageditor.tags.CoverChoice
import com.lhacenmed.sona.feature.tageditor.tags.TagField

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
    // Under the names, as a collection's Play is under its header: the song is heard while its tags are told apart.
    FilledTonalButton(onClick = onTogglePlayback, shapes = buttonPressShapes(), modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Text(if (isPlaying) "Pause" else "Play", modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
    }

    ColumnSection(title = "Search") {
        SearchFields(
            artist = draft.queryArtist,
            title = draft.queryTitle,
            onArtistChange = { draft.queryArtist = it },
            onTitleChange = { draft.queryTitle = it },
            onSearch = draft::search,
        )
    }

    HorizontalDivider()
    ColumnSection(title = "Best match") {
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
    ColumnSection(title = "Tags") {
        TagFields(tags = tags, onChange = draft::setField)
    }

    HorizontalDivider()
    ColumnSection(title = "Covers") {
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
        title = "Lyrics",
        actions = listOf(TopBarAction(label = "Edit lyrics", icon = Icons.Filled.Edit, onClick = onEditLyrics)),
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
        ColumnSection(title = "Other matches") {
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
