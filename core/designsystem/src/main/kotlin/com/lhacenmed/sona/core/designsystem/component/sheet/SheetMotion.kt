@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.core.designsystem.component.sheet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A part of a sheet shown while [visible]: faded in as the sheet grows to it, and faded out before the sheet shrinks from it.
 * Its size is left to the sheet's own animation - see SheetFrame - so the two never pull against each other.
 */
@Composable
fun SheetReveal(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        content()
    }
}

/**
 * What a sheet shows for [state], crossfaded into what it shows for the next - an option switched, a page turned. It takes its
 * new size at once, and the sheet animates to it.
 */
@Composable
fun <S> SheetSwap(state: S, modifier: Modifier = Modifier, content: @Composable (S) -> Unit) {
    val fadeIn = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())
    val fadeOut = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        transitionSpec = { fadeIn togetherWith fadeOut using SizeTransform(clip = false) { _, _ -> snap() } },
        label = "sheet swap",
    ) {
        content(it)
    }
}
