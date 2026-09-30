package com.lhacenmed.sona.feature.settings.updates

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsAvatar
import com.lhacenmed.sona.feature.settings.component.SettingsLazyList
import com.lhacenmed.sona.feature.settings.component.SettingsLinkItem
import com.lhacenmed.sona.feature.settings.component.settingsLoad
import com.lhacenmed.sona.feature.update.github.Commit
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * What is coming next: the development branch's latest commits, each opening on GitHub - ArchiveTune's
 * commit history.
 */
data object CommitsScreen : Screen {
    override val titleRes: Int get() = R.string.updates_recent_commits

    @Composable
    override fun Content() {
        val viewModel: CommitsViewModel = hiltViewModel()
        val commits by viewModel.commits.load.collectAsStateWithLifecycle()

        SettingsLazyList {
            settingsLoad(commits, onRetry = viewModel.commits::retry) { loaded ->
                items(loaded, key = Commit::sha) { commit ->
                    SettingsLinkItem(
                        title = commit.message,
                        summary = commit.byline(),
                        url = commit.url,
                        leadingContent = { SettingsAvatar(commit.authorAvatarUrl) },
                    )
                }
            }
        }
    }
}

/** "a1b2c3d · LhacenMed · Sep 26": which commit, whose, and when. */
private fun Commit.byline(): String =
    listOf(sha, author, formatCommitDate(date)).filter { it.isNotEmpty() }.joinToString(" · ")

/** "Sep 26" - ArchiveTune's commit date. */
private fun formatCommitDate(isoDate: String): String =
    runCatching {
        val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        SimpleDateFormat("MMM d", Locale.getDefault()).format(input.parse(isoDate)!!)
    }.getOrElse { isoDate.take(10) }
