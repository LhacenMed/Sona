package com.lhacenmed.sona.feature.download.tools

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.designsystem.component.InfoSeparator
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.SonaEmptyState
import com.lhacenmed.sona.core.designsystem.component.SonaListRow
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialogOption
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaConfirmationDialog
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.component.fab.ExtendedFloatingActionButtonContent
import com.lhacenmed.sona.core.designsystem.component.fab.SonaExtendedFloatingActionButtons
import com.lhacenmed.sona.core.designsystem.component.fastscroll.FastScroller
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.component.selectAllAction
import com.lhacenmed.sona.core.designsystem.component.toTopBarSelection
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.model.DownloadWatch
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.download.DownloadScreen
import com.lhacenmed.sona.feature.download.R
import com.lhacenmed.sona.feature.download.WatchChecker
import com.lhacenmed.sona.feature.download.linkIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/**
 * Watched channels and playlists - YTDLnis's observed sources: each looked at twice a day, every new video on it downloaded as
 * the user downloads. Added from the button, by link; looked at now from the bar; removed many at a time from a selection.
 */
data object WatchesScreen : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val viewModel: WatchesViewModel = hiltViewModel()
        val watches by viewModel.watches.collectAsStateWithLifecycle()
        val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
        val selection = rememberSelectionState()
        var isAdding by rememberSaveable { mutableStateOf(false) }
        var isRemoving by remember { mutableStateOf(false) }

        SonaExtendedFloatingActionButtons(
            listOf(ExtendedFloatingActionButtonContent(label = stringResource(R.string.download_watches_add), icon = SonaIcons.Add, onClick = { isAdding = true }, isPrimary = true)),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(
                title = stringResource(R.string.download_watches_title),
                onNavigateBack = navigator::back,
                actions = listOf(
                    TopBarAction(stringResource(R.string.download_watches_check), SonaIcons.Refresh, enabled = watches.isNotEmpty() && !isBusy) {
                        viewModel.checkNow()
                    },
                ),
                selection = selection.toTopBarSelection(
                    actions = listOf(
                        selection.selectAllAction(watches.map(DownloadWatch::id)),
                        TopBarAction(stringResource(R.string.download_watches_remove), SonaIcons.Delete) { isRemoving = true },
                    ),
                    onMoreOptions = null,
                ),
            )
            // Held in place whether or not it shows, so the list does not jump as a look starts and ends.
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().alpha(if (isBusy) 1f else 0f))
            if (watches.isEmpty()) {
                SonaEmptyState(title = stringResource(R.string.download_watches_empty_title), message = stringResource(R.string.download_watches_empty_message))
                return@Column
            }
            val listState = rememberLazyListState()
            FastScroller(listState, Modifier.fillMaxSize()) { overscrollEffect ->
                LazyColumn(
                    state = listState,
                    overscrollEffect = overscrollEffect,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
                ) {
                    items(watches, key = DownloadWatch::id) { watch ->
                        SonaListRow(
                            title = watch.title,
                            subtitle = listOf(watch.url, context.resources.getQuantityString(R.plurals.download_watches_known, watch.knownIds.size, watch.knownIds.size)).joinToString(InfoSeparator),
                            selection = selection,
                            selectionKey = watch.id,
                            // Its videos, opened on the download screen - to pick from as any playlist's.
                            onClick = { navigator.go(DownloadScreen(watch.url)) },
                            onOpenOptions = null,
                            cover = { Icon(SonaIcons.Subscriptions, contentDescription = null) },
                        )
                    }
                }
            }
        }

        if (isAdding) {
            AddWatchDialog(
                onAdd = { url, latest ->
                    viewModel.add(url, latest) { failure -> context.toast(failure ?: context.getString(R.string.download_watches_added)) }
                },
                onDismiss = { isAdding = false },
            )
        }

        if (isRemoving) {
            val ids = selection.selectedKeys.filterIsInstance<Long>()
            SonaConfirmationDialog(
                title = stringResource(R.string.download_watches_remove_title),
                message = context.resources.getQuantityString(R.plurals.download_watches_remove_message, ids.size, ids.size),
                confirmLabel = stringResource(R.string.download_watches_remove),
                successMessage = stringResource(R.string.download_watches_removed),
                failureMessage = stringResource(R.string.download_delete_failed),
                onDismiss = { isRemoving = false },
                operation = { onFinished ->
                    viewModel.remove(ids)
                    selection.clear()
                    onFinished(true)
                },
            )
        }
    }
}

/** How many of a channel's videos it can be watched from: none - only what comes after - or its latest few. */
private val LatestToDownload = listOf(0, 5, 10, 25)

/** A link to watch, and whether its latest videos are downloaded now too, or only what comes after. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AddWatchDialog(onAdd: (url: String, latest: Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val link = rememberTextFieldState()
    val url = linkIn(link.text.toString())
    var latest by rememberSaveable { mutableIntStateOf(0) }
    val cancelLabel = stringResource(R.string.download_cancel)
    val addLabel = stringResource(R.string.download_watches_add)
    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.download_watches_add),
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = addLabel, enabled = url != null, onClick = {
                url?.let { onAdd(it, latest) }
                onDismiss()
            })
        },
    ) {
        Text(stringResource(R.string.download_watches_add_message))
        OutlinedTextField(
            state = link,
            label = { Text(stringResource(R.string.download_link)) },
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            onKeyboardAction = {
                url?.let {
                    onAdd(it, latest)
                    onDismiss()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Column(modifier = Modifier.selectableGroup()) {
            LatestToDownload.forEach { count ->
                SonaDialogOption(
                    label = if (count == 0) stringResource(R.string.download_watches_only_new) else context.resources.getQuantityString(R.plurals.download_watches_latest, count, count),
                    selected = count == latest,
                    onClick = { latest = count },
                )
            }
        }
    }
}

@HiltViewModel
internal class WatchesViewModel @Inject constructor(
    private val checker: WatchChecker,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val watches: StateFlow<List<DownloadWatch>> = checker.watches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isBusy = MutableStateFlow(false)

    /** Whether a channel is being read - added, or every one looked at. */
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    /** Watches [url] - its [latest] videos downloaded now - saying why not to [onDone] where it cannot be - null once it is watched. */
    fun add(url: String, latest: Int, onDone: (String?) -> Unit) = busy {
        onDone(
            try {
                checker.add(url, latest)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: context.getString(R.string.download_watch_not_found)
            },
        )
    }

    fun checkNow() = busy { checker.checkAll() }

    fun remove(ids: Collection<Long>) {
        viewModelScope.launch { checker.remove(ids) }
    }

    private fun busy(work: suspend () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                work()
            } finally {
                _isBusy.value = false
            }
        }
    }
}
