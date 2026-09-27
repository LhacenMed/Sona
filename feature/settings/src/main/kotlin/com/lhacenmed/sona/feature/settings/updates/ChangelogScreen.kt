package com.lhacenmed.sona.feature.settings.updates

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.section.rememberSectionListState
import com.lhacenmed.sona.core.designsystem.component.section.section
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsLazyList
import com.lhacenmed.sona.feature.settings.component.SettingsLoad
import com.lhacenmed.sona.feature.settings.component.SettingsLoadStatus
import com.lhacenmed.sona.feature.update.ui.ReleaseNotes
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Every release on the chosen channel, newest first, with its notes - ArchiveTune's `ChangelogScreen`: each
 * release a section, titled by its version and the day it came out, whose long notes fold away to reach the
 * releases under it.
 */
data object ChangelogScreen : Screen {
    override val titleRes: Int get() = R.string.changelog_title

    @Composable
    override fun Content() {
        val viewModel: ChangelogViewModel = hiltViewModel()
        val releases by viewModel.releases.load.collectAsStateWithLifecycle()
        val listState = rememberLazyListState()
        val sections = rememberSectionListState(listState)

        SettingsLazyList(listState) {
            item(key = "status") { SettingsLoadStatus(releases, onRetry = viewModel.releases::retry) }
            (releases as? SettingsLoad.Loaded)?.items?.forEachIndexed { index, release ->
                section(
                    key = release.tagName,
                    title = listOf(release.versionName, formatReleaseDate(release.publishedAt)).joinToString(" · "),
                    state = sections,
                    hasDividerAbove = index > 0,
                ) {
                    release.notes?.let { notes ->
                        item(key = "notes-${release.tagName}") {
                            ReleaseNotes(
                                markdown = notes,
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "September 26, 2026", or the date as GitHub wrote it when it cannot be read. */
private fun formatReleaseDate(isoDate: String): String =
    runCatching {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate.substring(0, 10))!!
        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(date)
    }.getOrDefault(isoDate)
