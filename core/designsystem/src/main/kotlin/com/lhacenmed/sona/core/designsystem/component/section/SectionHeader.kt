@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.core.designsystem.component.section

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.R
import com.lhacenmed.sona.core.designsystem.component.SonaIconButtonGroup
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.iconButton

/**
 * The heading of one section of a list - "Albums", "Tracks", "Contributors": Auxio's `item_header`, the one
 * heading every sectioned list in the app has, a detail screen's and a settings screen's alike. Only ever
 * laid out by [section] and [ColumnSection].
 *
 * Every section folds: a press anywhere on its heading collapses it to the heading alone and back - the
 * widest target there is, so the heading needs no button of its own for it. What acts on the section sits
 * at its end - its [actions], as one group of buttons like a top bar's - and takes its own presses.
 *
 * It stays at the top of its list while its section scrolls under it, so it is painted in the rows' own
 * colour, and takes every press on it: a heading that let one through would press the row it is pinned
 * over, out of sight. While [isPinned] - rows passing under it - a divider along its bottom edge parts it
 * from them. [isPinned] is read only as it changes, so a scroll recomposes nothing here.
 */
@Composable
internal fun SectionHeader(
    title: String,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    isPinned: () -> Boolean,
    modifier: Modifier = Modifier,
    actions: List<TopBarAction> = emptyList(),
) {
    val currentIsPinned by rememberUpdatedState(isPinned)
    val pinned by remember { derivedStateOf { currentIsPinned() } }
    val dividerAlpha by animateFloatAsState(if (pinned) 1f else 0f, SectionMotion.fade(), label = "sectionDivider")
    val dividerColor = DividerDefaults.color

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .drawWithContent {
                drawContent()
                // Read while drawing, so the divider fading in and out redraws the heading and nothing more.
                val alpha = dividerAlpha
                if (alpha > 0f) {
                    val thickness = DividerDefaults.Thickness.toPx()
                    drawRect(
                        color = dividerColor,
                        topLeft = Offset(0f, size.height - thickness),
                        size = Size(size.width, thickness),
                        alpha = alpha,
                    )
                }
            }
            .clickable(
                onClickLabel = stringResource(if (isCollapsed) R.string.section_expand else R.string.section_collapse),
                onClick = onToggle,
            )
            .semantics { heading() }
            .heightIn(min = 48.dp)
            .padding(start = 16.dp, end = if (actions.isEmpty()) 16.dp else 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f),
        )
        if (actions.isNotEmpty()) {
            SonaIconButtonGroup {
                actions.forEach { action ->
                    iconButton(icon = action.icon, label = action.label, onClick = action.onClick, enabled = action.enabled)
                }
            }
        }
    }
}
