package com.lhacenmed.sona.feature.download

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SonaActionButtonGroup
import com.lhacenmed.sona.core.designsystem.component.SonaCoverArt
import com.lhacenmed.sona.core.designsystem.component.SonaListRow
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaColumnBottomSheet
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.model.DownloadKind

/**
 * What the library holds of the link open, as it is looked for - see [LibraryCheck] - for [kind], among [urls]: looking, with what
 * it may have found already; then what it found, or that it holds none. Always there, so a sheet stays still as it is answered.
 */
@Composable
internal fun LibraryCheckLine(check: LibraryCheck?, kind: DownloadKind, urls: Set<String>, modifier: Modifier = Modifier) {
    val found = check?.of(kind).orEmpty().filterKeys { it in urls }
    val first = found.values.firstOrNull()?.firstOrNull()
    val isLooking = check != null && !check.isDone
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            isLooking -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            first == null -> Icon(SonaIcons.LibraryAdd, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            else -> Icon(SonaIcons.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
        Text(
            text = when {
                first != null && urls.size > 1 -> pluralStringResource(R.plurals.download_copies_some, found.size, found.size)
                first != null -> stringResource(if (first.isExact) R.string.download_copy_exact else R.string.download_copy_similar, first.title)
                isLooking -> stringResource(R.string.download_checking_library)
                else -> stringResource(R.string.download_copy_none)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * What was asked, as the library is looked through for it - opened the moment Download is pressed, before the look is over: what
 * it finds, as it finds it, each picked to be downloaded again, none to begin with; then Download, of the rest and those picked -
 * or, while it still looks, Download once it is checked, which downloads at once where it finds none of it. Cancel first.
 */
@Composable
internal fun AlreadySavedSheet(sheet: LinkSheet.AlreadySaved, viewModel: DownloadViewModel) {
    val context = LocalContext.current
    val check by viewModel.libraryCheck.collectAsStateWithLifecycle()
    val downloadsOnceChecked by viewModel.downloadsOnceChecked.collectAsStateWithLifecycle()
    val picks = rememberSelectionState()
    val copies = check?.of(sheet.kind).orEmpty()
    val urls = sheet.requests.map(DownloadRequest::url).toSet()
    val found = sheet.requests.filter { it.url in copies }
    val isDone = check?.isDone != false
    val count = sheet.requests.size - found.size + found.count { picks.isSelected(it.url) }
    val cancelLabel = stringResource(R.string.download_cancel)
    val downloadLabel = when {
        !isDone -> stringResource(if (downloadsOnceChecked) R.string.download_once_checked_waiting else R.string.download_once_checked)
        sheet.requests.size == 1 -> stringResource(if (found.isEmpty()) R.string.download_download else R.string.download_fix_download_anyway)
        else -> pluralStringResource(R.plurals.download_download_count, count, count)
    }
    SonaColumnBottomSheet(onDismissRequest = viewModel::onDismissLink, title = stringResource(R.string.download_failure_already_saved)) { scope ->
        LibraryCheckLine(check, sheet.kind, urls, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        if (found.isNotEmpty()) {
            Text(
                pluralStringResource(R.plurals.download_copies_found, found.size, found.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(found, key = DownloadRequest::url) { request ->
                val copy = copies.getValue(request.url).first()
                SonaListRow(
                    title = request.title ?: hostOf(request.url),
                    subtitle = context.getString(if (copy.isExact) R.string.download_copy_exact else R.string.download_copy_similar, copy.title),
                    selection = picks,
                    selectionKey = request.url,
                    // Picking is all this list is for: a tap picks.
                    onClick = { picks.toggle(request.url) },
                    onOpenOptions = null,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    cover = { isSelected -> SonaCoverArt(coverArtUri = request.thumbnailUrl, contentDescription = null, isSelected = isSelected) },
                )
            }
        }
        SonaActionButtonGroup(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            actionButton(label = cancelLabel, onClick = { scope.dismiss() })
            actionButton(label = downloadLabel, enabled = !(isDone && sheet.requests.size > 1 && count == 0), onClick = {
                when {
                    !isDone -> viewModel.onDownloadOnceChecked(sheet)
                    sheet.requests.size == 1 -> viewModel.onDownloadAgain(sheet, urls)
                    else -> viewModel.onDownloadAgain(sheet, found.map { it.url }.filter(picks::isSelected).toSet())
                }
            })
        }
    }
}
