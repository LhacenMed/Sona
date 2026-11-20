package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow

/**
 * A text field's own state for a [text] held elsewhere - a screen's query, a view model's draft: it starts from [text], tells
 * [onTextChange] what is typed, and takes [text] up wherever it is changed from outside - a link field cleared once its link
 * is opened. The field holds what is typed itself, so typing is never put back by a moment's echo of what it said before:
 * [text] coming back as what it last said is that echo, anything else a change from outside.
 */
@Composable
fun rememberSyncedTextFieldState(text: String, onTextChange: (String) -> Unit): TextFieldState {
    val state = rememberTextFieldState(text)
    val onChange by rememberUpdatedState(onTextChange)
    // What the field last said it holds - or was last given.
    val said = remember { arrayOf(text) }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { typed ->
            if (typed != said[0]) {
                said[0] = typed
                onChange(typed)
            }
        }
    }
    LaunchedEffect(text) {
        if (text != said[0]) {
            said[0] = text
            state.setTextAndPlaceCursorAtEnd(text)
        }
    }
    return state
}
