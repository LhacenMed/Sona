package com.lhacenmed.sona.feature.download

import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.CookieShape
import com.lhacenmed.sona.core.designsystem.component.InfoSeparator
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.model.DownloadStatus
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.download.settings.DownloadsSettingsScreen
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons

/**
 * Where links become songs and videos - Seal's home: a large title saying where things stand, one field a link is pasted or
 * typed into, then what is downloading now and what was saved today - played with a tap, put right where it failed. Every
 * download before is a tap away, in the [HistoryScreen]. A link pasted, typed or shared - [link] - opens in a sheet, is
 * checked at a glance, and is downloaded as audio or video with a tap.
 */
data class DownloadScreen(val link: String? = null) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: DownloadViewModel = hiltViewModel()
        val sections by viewModel.sections.collectAsStateWithLifecycle()
        val running by viewModel.running.collectAsStateWithLifecycle()
        val linkEntry by viewModel.linkEntry.collectAsStateWithLifecycle()
        val linkSheet by viewModel.linkSheet.collectAsStateWithLifecycle()
        val state = rememberDownloadListState()

        // The link this was opened with, opened once - not again as the screen comes back.
        var hasTakenLink by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(link) {
            if (!hasTakenLink && link != null) viewModel.open(link)
            hasTakenLink = true
        }

        val downloading = sections?.downloading.orEmpty()
        val today = sections?.today.orEmpty()
        val listed = downloading + today
        val runningCount = downloading.count { it.id in running }
        val pausedCount = downloading.count { it.status == DownloadStatus.PAUSED && it.id !in running }
        val waiting = downloading.size - runningCount - pausedCount
        val summary = listOfNotNull(
            runningCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.download_downloading_count, it, it) },
            pausedCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.download_paused_count, it, it) },
            waiting.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.download_waiting_count, it, it) },
        ).joinToString(InfoSeparator).ifEmpty { stringResource(R.string.download_ready) }
        val title = stringResource(R.string.download_title)
        val seeAll = TopBarAction(stringResource(R.string.download_see_all), SonaIcons.KeyboardArrowRight) { navigator.go(HistoryScreen) }

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(
                // The title is the list's own, large, over the link field.
                title = "",
                onNavigateBack = navigator::back,
                actions = listOf(
                    TopBarAction(stringResource(R.string.download_history), SonaIcons.History) { navigator.go(HistoryScreen) },
                    TopBarAction(stringResource(R.string.download_settings), SonaIcons.Settings) { navigator.go(DownloadsSettingsScreen) },
                ),
                selection = state.topBarSelection(listed),
            )
            DownloadList(
                groups = listOf(
                    DownloadGroup("downloading", stringResource(R.string.download_section_downloading), downloading),
                    DownloadGroup("today", stringResource(R.string.download_section_today), today, actions = listOf(seeAll)),
                ),
                viewModel = viewModel,
                state = state,
                header = {
                    screenTitle(title, summary)
                    item(key = "link-field") { LinkField(linkEntry, viewModel) }
                },
                footer = {
                    if (sections != null && listed.isEmpty()) item(key = "empty") { EmptyNote() }
                },
            )
        }

        linkSheet?.let { LinkSheet(it, viewModel) }
        DownloadListSheets(state, listed, viewModel)
    }
}

/**
 * The one field a link is given in: typed - opened the moment it is a link - or pasted with the button inside it, which reads
 * the clipboard without the keyboard. With no link to paste, the field takes the keyboard to have one typed.
 */
@Composable
private fun LinkField(text: String, viewModel: DownloadViewModel) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    TextField(
        value = text,
        onValueChange = viewModel::onLinkEntryChange,
        placeholder = { Text(stringResource(R.string.download_link_hint)) },
        leadingIcon = { Icon(SonaIcons.Link, contentDescription = null) },
        trailingIcon = {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)
                    if (!viewModel.onPaste(clipboard?.toString())) focusRequester.requestFocus()
                },
                shapes = buttonPressShapes(),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.padding(end = 6.dp),
            ) {
                Icon(SonaIcons.ContentPaste, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(R.string.download_paste), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp).focusRequester(focusRequester),
    )
}

/** Under the field while nothing is downloading or saved today: what the screen is for, and the two ways a link comes in. */
@Composable
private fun EmptyNote() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.size(112.dp).clip(CookieShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(SonaIcons.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(48.dp))
        }
        Text(stringResource(R.string.download_empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.download_empty_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
