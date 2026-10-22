package com.lhacenmed.sona.core.designsystem.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * A [BackHandler] whose [onBack] never answers the back that closes the keyboard.
 *
 * Back while the keyboard is up is the keyboard's - it closes it, and nothing else. Android does not always
 * keep it so: a screen's own back handler can be handed that same back on its way to the keyboard, and it
 * would then ask whether to leave the edits being typed, or close the search being typed into. So while the
 * keyboard shows, a back only puts it away, and lets go of the field it was typing into; the next is the
 * screen's.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardAwareBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val isKeyboardShown = WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(enabled = enabled) {
        if (isKeyboardShown) {
            keyboard?.hide()
            focusManager.clearFocus()
        } else {
            onBack()
        }
    }
}
