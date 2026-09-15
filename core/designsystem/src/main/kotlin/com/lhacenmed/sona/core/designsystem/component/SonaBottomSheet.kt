package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
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
 * and a half-open one hides the buttons at its bottom. The content scrolls, so a sheet opened in
 * landscape can still reach its last row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonaBottomSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable SonaBottomSheetScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val latestOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val sheetScope = remember(sheetState, coroutineScope) {
        SonaBottomSheetScope {
            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { latestOnDismissRequest() }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
            sheetScope.content()
        }
    }
}
