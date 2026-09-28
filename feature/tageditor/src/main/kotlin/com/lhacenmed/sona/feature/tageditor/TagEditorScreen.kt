package com.lhacenmed.sona.feature.tageditor

import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lhacenmed.sona.core.common.permission.AppPermission
import com.lhacenmed.sona.core.data.contentUri
import com.lhacenmed.sona.core.designsystem.component.CoverArtDefaults
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.SonaCoverImage
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.screen.screenList
import com.lhacenmed.sona.core.designsystem.component.section.ColumnSection
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.tageditor.tags.CoverChoice
import com.lhacenmed.sona.feature.tageditor.tags.TagField
import java.io.File

/**
 * Editing the track [trackId]'s tags - AutomaTag's editor, in Sona's design.
 *
 * It opens looking the track up already, under the likeliest reading of its names - laid out at the top, to be
 * changed and searched again. What it finds fills the screen as it arrives: the best match, applied whole with
 * one press, every tag to change by hand, every cover to choose from - the gallery's included, found or not -
 * every set of lyrics every source has, and the other matches; each list narrowed to one source in a tap.
 * Nothing reaches the file until it is saved; back leaves it as it was.
 *
 * Saving writes the file itself. Sona does so freely where it may manage all files; elsewhere Android asks the
 * user first, once for this file - and a file MediaStore has not indexed, which Android's request cannot name,
 * needs that access outright.
 */
data class TagEditorScreen(val trackId: Long) : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val viewModel = hiltViewModel<TagEditorViewModel, TagEditorViewModel.Factory>(
            creationCallback = { factory -> factory.create(trackId) },
        )
        val track = viewModel.track

        lateinit var save: () -> Unit
        val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) save()
        }
        val allFilesAccessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (AppPermission.FILE_CHANGES.isGranted(context)) save()
        }
        val writePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) save()
        }
        val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) viewModel.pickDeviceCover(uri.toString())
        }
        save = {
            viewModel.save { outcome ->
                when (outcome) {
                    SaveOutcome.Saved -> {
                        context.toast("Tags saved")
                        navigator.back()
                    }
                    is SaveOutcome.NeedsConsent -> consentLauncher.launch(IntentSenderRequest.Builder(outcome.request).build())
                    is SaveOutcome.Failed -> context.toast(outcome.message)
                }
            }
        }
        val requestSave: () -> Unit = {
            val permission = AppPermission.FILE_CHANGES
            val runtimePermission = permission.runtimePermission
            when {
                track == null -> Unit
                permission.isGranted(context) -> save()
                runtimePermission != null -> writePermissionLauncher.launch(runtimePermission)
                track.isManuallyScanned -> allFilesAccessLauncher.launch(permission.settingsIntent(context))
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    val request = MediaStore.createWriteRequest(context.contentResolver, listOf(track.contentUri))
                    consentLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                }
                else -> save()
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(
                title = "Edit tags",
                onNavigateBack = navigator::back,
                actions = listOf(
                    TopBarAction(label = "Save", icon = Icons.Filled.Check, enabled = viewModel.hasChanges && !viewModel.isSaving) {
                        requestSave()
                    },
                ),
            )
            if (track == null) return@Column

            val scrollState = rememberScrollState()
            val draft = viewModel.draft
            val best = viewModel.matches.firstOrNull()
            val cover = viewModel.cover
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .screenList(scrollState)
                    .verticalScroll(scrollState)
                    .padding(bottom = LocalBottomContentPadding.current),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The track as it will be saved: the cover chosen, and the names as they stand.
                SonaCoverImage(
                    coverArtUri = when (cover) {
                        CoverChoice.Own -> track.coverArtUri
                        is CoverChoice.Web -> cover.url
                        is CoverChoice.Device -> cover.uri
                    },
                    contentDescription = null,
                    cornerRadius = CoverArtDefaults.DetailHeaderCornerRadius,
                    modifier = Modifier.padding(16.dp).size(CoverArtDefaults.DetailHeaderSize),
                )
                HeaderText(draft[TagField.TITLE].ifBlank { track.title }, MaterialTheme.typography.titleLargeEmphasized)
                HeaderText(draft[TagField.ARTIST].ifBlank { track.artist }, MaterialTheme.typography.bodyLarge)

                ColumnSection(title = "Search", scrollState = scrollState) {
                    SearchFields(
                        artist = viewModel.queryArtist,
                        title = viewModel.queryTitle,
                        onArtistChange = { viewModel.queryArtist = it },
                        onTitleChange = { viewModel.queryTitle = it },
                        onSearch = viewModel::search,
                    )
                }

                HorizontalDivider()
                ColumnSection(title = "Best match", scrollState = scrollState) {
                    SourceFilter(
                        sources = viewModel.catalogueNames,
                        selected = viewModel.catalogueFilter,
                        onSelect = viewModel::filterCatalogue,
                    )
                    BestMatchCard(
                        lookup = viewModel.matchLookup,
                        best = best?.let(viewModel::completed),
                        isApplied = best != null && viewModel.appliedMatch == best,
                        onApply = { best?.let(viewModel::apply) },
                        onRetry = viewModel::search,
                    )
                }

                HorizontalDivider()
                ColumnSection(title = "Tags", scrollState = scrollState) {
                    TagFields(tags = draft, onChange = viewModel::setField)
                }

                HorizontalDivider()
                ColumnSection(title = "Covers", scrollState = scrollState) {
                    CoverChoices(
                        ownCoverUri = track.coverArtUri,
                        deviceCoverUri = viewModel.deviceCoverUri,
                        matchCovers = viewModel.matches.filter { it.coverUrl.isNotBlank() }.distinctBy { it.coverUrl },
                        selected = cover,
                        onPickFromGallery = {
                            galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onSelect = viewModel::chooseCover,
                    )
                }

                HorizontalDivider()
                ColumnSection(title = "Lyrics", scrollState = scrollState) {
                    SourceFilter(
                        sources = viewModel.lyricsSourceNames,
                        selected = viewModel.lyricsFilter,
                        onSelect = viewModel::filterLyrics,
                        counts = viewModel.lyricsResults.groupingBy { it.source }.eachCount(),
                    )
                    viewModel.visibleLyrics.forEach { found ->
                        LyricsResultCard(
                            found = found,
                            trackDurationMs = track.durationMs,
                            isApplied = draft[TagField.LYRICS] == found.lyrics.text,
                            onApply = { viewModel.applyLyrics(found) },
                        )
                    }
                    LyricsSearchStatus(isSearching = viewModel.isSearchingLyrics, hasResults = viewModel.visibleLyrics.isNotEmpty())
                }

                val others = viewModel.matches.drop(1)
                if (others.isNotEmpty()) {
                    HorizontalDivider()
                    ColumnSection(title = "Other matches", scrollState = scrollState) {
                        others.forEach { match ->
                            MatchRow(
                                match = match,
                                isApplying = viewModel.applyingMatch == match,
                                isApplied = viewModel.appliedMatch == match,
                                onApply = { viewModel.apply(match) },
                            )
                        }
                    }
                }

                HorizontalDivider()
                ColumnSection(title = "File", scrollState = scrollState) {
                    FileFact(label = "Duration", value = formatDuration(track.durationMs))
                    FileFact(label = "Format", value = File(track.path).extension.uppercase())
                    viewModel.bitrateKbps?.let { FileFact(label = "Bitrate", value = "$it kbps") }
                    FileFact(label = "Path", value = track.path)
                }
            }
        }
    }
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
