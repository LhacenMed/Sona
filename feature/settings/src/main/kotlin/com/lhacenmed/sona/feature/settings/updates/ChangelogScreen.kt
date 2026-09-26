@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.settings.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.datastore.UpdateChannel
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.fab.screenList
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.designsystem.theme.roundedShape
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.update.github.Release
import com.lhacenmed.sona.feature.update.github.Releases
import com.lhacenmed.sona.feature.update.ui.ReleaseNotes
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Every release on [channel], newest first, with its notes - ArchiveTune's `ChangelogScreen`: the kept list
 * at once, then GitHub's.
 */
data class ChangelogScreen(val channel: UpdateChannel) : Screen {
    override val titleRes: Int get() = R.string.changelog_title

    @Composable
    override fun Content() {
        val context = LocalContext.current
        var releases by remember { mutableStateOf(Releases.cached(context, channel)) }
        var failure by remember { mutableStateOf<String?>(null) }
        var attempt by remember { mutableIntStateOf(0) }
        var isLoading by remember { mutableStateOf(releases.isEmpty()) }

        LaunchedEffect(attempt) {
            Releases.all(context, channel, forceRefresh = true)
                .onSuccess {
                    releases = it
                    failure = null
                }
                .onFailure { if (releases.isEmpty()) failure = it.message.orEmpty() }
            isLoading = false
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> CircularWavyProgressIndicator(modifier = Modifier.align(Alignment.Center))

                failure != null ->
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.changelog_load_failed),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                isLoading = true
                                failure = null
                                attempt++
                            },
                            shapes = buttonPressShapes(),
                        ) { Text(stringResource(R.string.retry)) }
                    }

                releases.isEmpty() ->
                    Text(
                        text = stringResource(R.string.changelog_no_releases),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    )

                else -> {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().screenList(listState),
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = LocalBottomContentPadding.current),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(releases, key = { it.tagName }) { release -> ReleaseCard(release) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: Release) {
    val formattedDate = remember(release.publishedAt) { formatReleaseDate(release.publishedAt) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = roundedShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = release.tagName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            release.notes?.let { notes ->
                Spacer(modifier = Modifier.height(8.dp))
                ReleaseNotes(markdown = notes)
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
