package com.lhacenmed.sona.core.designsystem.component.section

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lhacenmed.sona.core.designsystem.component.TopBarAction

/**
 * One section of a column - [section], for a list short enough to be one column: its [SectionHeader], then
 * its [content], which a press on the heading folds away and back. It opens expanded, and stays as it was
 * left when the screen comes back.
 *
 * Folding closes the section from its bottom up with the motion a lazy section's rows move by, so the
 * column's length - and its scroll, and whatever follows it - follows frame by frame.
 */
@Composable
fun ColumnScope.ColumnSection(
    title: String,
    modifier: Modifier = Modifier,
    actions: List<TopBarAction> = emptyList(),
    content: @Composable ColumnScope.() -> Unit,
) {
    var isCollapsed by rememberSaveable(title) { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = title,
            isCollapsed = isCollapsed,
            onToggle = { isCollapsed = !isCollapsed },
            actions = actions,
        )
        val fade = SectionMotion.fade()
        AnimatedVisibility(
            visible = !isCollapsed,
            enter = expandVertically(SectionMotion.size, expandFrom = Alignment.Top) + fadeIn(fade),
            exit = shrinkVertically(SectionMotion.size, shrinkTowards = Alignment.Top) + fadeOut(fade),
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}
