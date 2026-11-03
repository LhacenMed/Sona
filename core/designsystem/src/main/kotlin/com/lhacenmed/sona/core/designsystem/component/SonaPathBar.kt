package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes

/**
 * Where a browser is: every level from the top - [levels], the open one last - each a button that opens it, pressed as
 * every button of the app is. The open one stands out, filled; the path is scrolled to show it whenever it changes.
 *
 * It scrolls with no overscroll: the app's rubber band, stretched by a drag at the path's end, would pull against
 * the pager a sheet lays its tabs out in - the glitch the tabs once had.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SonaPathBar(levels: List<String>, onOpen: (level: Int) -> Unit, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    LaunchedEffect(levels) { scrollState.animateScrollTo(scrollState.maxValue) }
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        Row(
            modifier = modifier.fillMaxWidth().horizontalScroll(scrollState).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            levels.forEachIndexed { index, level ->
                if (index > 0) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                PathButton(level, isOpen = index == levels.lastIndex, onClick = { onOpen(index) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PathButton(label: String, isOpen: Boolean, onClick: () -> Unit) {
    val text = @Composable { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    if (isOpen) {
        FilledTonalButton(onClick = onClick, shapes = buttonPressShapes()) { text() }
    } else {
        TextButton(onClick = onClick, shapes = buttonPressShapes()) { text() }
    }
}
