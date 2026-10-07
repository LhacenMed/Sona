package com.lhacenmed.sona.core.designsystem.component.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.effect.ProvideSonaHaptics
import kotlinx.coroutines.launch

/** What a sheet's content can do to the sheet it sits in. */
@Stable
class SonaBottomSheetScope internal constructor(private val dismissAnimated: () -> Unit) {

    /** Slides the sheet away, then reports it dismissed - how a button inside the sheet closes it. */
    fun dismiss() = dismissAnimated()
}

/**
 * The bottom sheet every screen opens: a drag handle, a title, then whatever the caller puts beneath.
 *
 * Closing from inside - Cancel, or a button that confirms - goes through [SonaBottomSheetScope.dismiss],
 * which lets the sheet slide away before [onDismissRequest] removes it; removing it straight away would
 * cut it off the screen mid-frame. It opens fully rather than half-way, because these sheets are short
 * and a half-open one hides the buttons at its bottom. The content scrolls beneath the title, so a sheet
 * opened in landscape can still reach its last row.
 */
@Composable
fun SonaBottomSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable SonaBottomSheetScope.() -> Unit,
) {
    SonaBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        header = { SheetTitle(title) },
        content = content,
    )
}

/**
 * The same sheet, with [header] standing in for the plain title - an options sheet's cover, type,
 * name and info line, in place of a single line of text. The header stays put while the content
 * beneath it scrolls.
 */
@Composable
fun SonaBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    content: @Composable SonaBottomSheetScope.() -> Unit,
) {
    SheetFrame(onDismissRequest, modifier) { sheetScope ->
        header()
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            sheetScope.content()
        }
    }
}

/**
 * The same sheet for a list that can run long - a queue, say: the rows are a lazy list, so only those in view
 * are drawn, however many there are. [listState] places it - opened at the row that matters.
 */
@Composable
fun SonaLazyBottomSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.(SonaBottomSheetScope) -> Unit,
) {
    SheetFrame(onDismissRequest, modifier) { sheetScope ->
        SheetTitle(title)
        LazyColumn(state = listState) { content(sheetScope) }
    }
}

@Composable
private fun SheetTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
    )
}

/**
 * The modal sheet every variant is: opened fully, closed animated from inside, with its own window's haptics.
 *
 * Its height never changes while it is dragged. Material's own top inset is the status bar less however far
 * the sheet has been dragged down, so a sheet whose content reaches the status bar would grow and shrink
 * with every pixel of a drag - and, with its height, the place it rests at - pulling itself back under the
 * finger instead of following it. Only a list longer than the screen by more than the status bar hid that.
 * The content is held below the status bar by a gap fixed at the bar's height instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetFrame(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.(SonaBottomSheetScope) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val latestOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val sheetScope = remember(sheetState, coroutineScope) {
        SonaBottomSheetScope {
            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { latestOnDismissRequest() }
        }
    }
    val statusBarGap = with(LocalDensity.current) { WindowInsets.safeDrawing.getTop(this).toDp() }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.padding(top = statusBarGap),
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
    ) {
        // Its own window, with its own haptics: gated as every window's are.
        ProvideSonaHaptics {
            content(sheetScope)
        }
    }
}
