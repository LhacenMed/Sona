package com.lhacenmed.sona.feature.download

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.InfoSeparator
import com.lhacenmed.sona.core.designsystem.component.SonaCoverArt
import com.lhacenmed.sona.core.designsystem.component.copyToClipboard
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaBottomSheet
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaOptionRow
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaOptionsSheetHeader
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.model.Download
import com.lhacenmed.sona.core.model.DownloadFailure
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.model.DownloadStatus
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.feature.download.tools.CookiesScreen
import com.lhacenmed.sona.feature.tageditor.autotag.AutoTagScreen

/**
 * One download, opened: what it is, and what can be done with it as it stands - stopped while it waits or runs; played,
 * tagged, downloaded again or shared once saved; and, where it failed, why in plain words with the one fix that helps, the
 * downloader's own words a tap further.
 */
@Composable
internal fun DownloadSheet(download: Download, viewModel: DownloadViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val songIds = if (download.status == DownloadStatus.DONE && download.choices.kind == DownloadKind.AUDIO) {
        viewModel.tracksOf(download).filterNot { it.isVideo }.map { it.id }
    } else {
        emptyList()
    }
    val copiedLabel = stringResource(R.string.download_link_copied)
    fun shareLink() = context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, download.url), null))

    SonaBottomSheet(
        onDismissRequest = onDismiss,
        header = {
            SonaOptionsSheetHeader(
                cover = { SonaCoverArt(coverArtUri = download.thumbnailUrl, contentDescription = null) },
                type = stringResource(download.choices.kind.labelRes),
                name = download.title ?: hostOf(download.url),
                info = listOfNotNull(download.uploader, download.site).joinToString(InfoSeparator),
            )
        },
    ) {
        when (download.status) {
            DownloadStatus.QUEUED, DownloadStatus.RUNNING -> SonaOptionRow(label = stringResource(R.string.download_cancel), icon = Icons.Filled.Close, onClick = {
                viewModel.onCancel(listOf(download.id))
                dismiss()
            })

            DownloadStatus.DONE -> {
                SonaOptionRow(label = stringResource(R.string.download_play), icon = Icons.Filled.PlayArrow, onClick = {
                    viewModel.onPlay(download)
                    dismiss()
                })
                if (songIds.isNotEmpty()) {
                    SonaOptionRow(label = stringResource(R.string.download_auto_tag), icon = Icons.Filled.LibraryMusic, onClick = {
                        navigator.go(AutoTagScreen(songIds))
                        dismiss()
                    })
                }
                SonaOptionRow(label = stringResource(R.string.download_again), icon = Icons.Filled.Download, onClick = {
                    viewModel.onDownloadAgain(listOf(download))
                    dismiss()
                })
                SonaOptionRow(label = stringResource(R.string.download_share_link), icon = Icons.Filled.Share, onClick = {
                    shareLink()
                    dismiss()
                })
            }

            DownloadStatus.FAILED, DownloadStatus.CANCELED -> {
                Unsaved(download, viewModel, onFixed = { dismiss() }, onSignIn = {
                    navigator.go(CookiesScreen(download.url))
                    dismiss()
                })
                SonaOptionRow(label = stringResource(R.string.download_copy_link), icon = Icons.Filled.ContentCopy, onClick = {
                    context.copyToClipboard(download.url, copiedLabel)
                    dismiss()
                })
            }
        }
    }
}

/** A download not saved: why - cancelled, or the reason it failed - and the one fix, then the downloader's words, folded away. */
@Composable
private fun Unsaved(download: Download, viewModel: DownloadViewModel, onFixed: () -> Unit, onSignIn: () -> Unit) {
    val context = LocalContext.current
    if (download.status == DownloadStatus.CANCELED) {
        SheetMessage(Icons.Filled.DoNotDisturbOn, stringResource(R.string.download_status_canceled), stringResource(R.string.download_canceled_message))
        FixButton(R.string.download_again, Icons.Filled.Download) {
            viewModel.onRetry(download)
            onFixed()
        }
        return
    }
    val presentation = (download.failure ?: DownloadFailure.FAILED).presentation
    SheetMessage(presentation.icon, stringResource(presentation.titleRes), stringResource(presentation.messageRes))
    FixButton(presentation.fix.labelRes, presentation.fix.icon) {
        when (presentation.fix) {
            FailureFix.TRY_AGAIN -> viewModel.onRetry(download)
            FailureFix.TRY_BEST_FORMAT -> viewModel.onRetryAtBestFormat(download)
            FailureFix.OPEN_LINK -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(download.url))) }
            FailureFix.SIGN_IN -> return@FixButton onSignIn()
            FailureFix.FREE_UP_SPACE -> runCatching { context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
            // It downloads by itself once the downloader is here - see DownloadQueue.
            FailureFix.GET_DOWNLOADER -> viewModel.onGetDownloader()
        }
        onFixed()
    }
    download.error?.let { ErrorDetails(it) }
}

/** What went wrong in the downloader's own words - folded away under its row, to be read, selected or copied. */
@Composable
internal fun ErrorDetails(error: String) {
    val context = LocalContext.current
    val copiedLabel = stringResource(R.string.download_error_copied)
    var showsDetails by remember { mutableStateOf(false) }
    SonaOptionRow(
        label = stringResource(R.string.download_error_details),
        icon = Icons.Filled.Code,
        onClick = { showsDetails = !showsDetails },
        trailingContent = { Icon(if (showsDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null) },
    )
    if (showsDetails) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            SelectionContainer {
                Text(error, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SonaOptionRow(label = stringResource(R.string.download_copy_error), icon = Icons.Filled.ContentCopy, onClick = { context.copyToClipboard(error, copiedLabel) })
    }
}

/** A sheet's one fix for what went wrong - filled, full width, the obvious next tap. */
@Composable
internal fun FixButton(labelRes: Int, icon: ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shapes = buttonPressShapes(),
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp).fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(stringResource(labelRes), modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
    }
}

/** What else the selection can do - beyond Select all and Delete, which the bar holds itself. */
@Composable
internal fun SelectionOptionsSheet(
    selected: List<Download>,
    viewModel: DownloadViewModel,
    onShareLinks: () -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val songIds = selected.filter { it.status == DownloadStatus.DONE && it.choices.kind == DownloadKind.AUDIO }
        .flatMap(viewModel::tracksOf).filterNot { it.isVideo }.map { it.id }
    SonaBottomSheet(title = LocalContext.current.resources.getQuantityString(R.plurals.download_selected, selected.size, selected.size), onDismissRequest = onDismiss) {
        SonaOptionRow(label = stringResource(R.string.download_again), icon = Icons.Filled.Download, onClick = {
            viewModel.onDownloadAgain(selected)
            onDone()
            dismiss()
        })
        SonaOptionRow(label = stringResource(R.string.download_share_links), icon = Icons.Filled.Share, onClick = {
            onShareLinks()
            onDone()
            dismiss()
        })
        if (songIds.isNotEmpty()) {
            SonaOptionRow(label = stringResource(R.string.download_auto_tag), icon = Icons.Filled.LibraryMusic, onClick = {
                navigator.go(AutoTagScreen(songIds))
                onDone()
                dismiss()
            })
        }
    }
}
