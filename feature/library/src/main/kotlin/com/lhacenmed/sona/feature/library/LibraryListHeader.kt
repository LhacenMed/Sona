@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhacenmed.sona.core.designsystem.component.sheet.label
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.motion.FadeInMillis
import com.lhacenmed.sona.core.designsystem.motion.FadeOutMillis
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.model.sort.SortDirection
import com.lhacenmed.sona.feature.library.sort.SortControl

/** The header's height, and its sort chip's. */
private val HeaderHeight = 44.dp
private val SortChipHeight = 32.dp

/** What the header says - how many, or how far the first scan has come. */
private val HeaderTextStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)

/**
 * The head of the list on screen, under the tabs: how much it holds - [countLabel], blank while it is read - and how it is
 * sorted, a chip opening its sort. While the library is read for the first time - [isFirstScan] - the expressive loading
 * indicator stands before the count, which climbs as the list fills. It scrolls away with the list - see
 * `collapsingHeaderTail`.
 */
@Composable
internal fun LibraryListHeader(
    countLabel: String?,
    sort: SortControl,
    isFirstScan: Boolean,
    onSort: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            .padding(horizontal = SonaComponentStyle.ContentHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .semantics { if (isFirstScan) liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = isFirstScan,
                enter = fadeIn(tween(FadeInMillis)) + expandHorizontally(),
                exit = fadeOut(tween(FadeOutMillis)) + shrinkHorizontally(),
            ) {
                LoadingIndicator(modifier = Modifier.padding(end = 10.dp).size(24.dp))
            }
            Text(text = countLabel.orEmpty(), style = HeaderTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SortChip(sort, onSort)
    }
}

@Composable
private fun SortChip(sort: SortControl, onSort: () -> Unit) {
    val order by remember(sort) { sort.order }.collectAsState(initial = sort.currentOrder())
    FilledTonalButton(
        onClick = onSort,
        shapes = buttonPressShapes(),
        modifier = Modifier.height(SortChipHeight),
        contentPadding = PaddingValues(start = 12.dp, end = 8.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(text = order.criterion.label(), style = HeaderTextStyle)
        SortDirectionIcon(order.direction, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Sort's two arrows, the one the order runs in drawn strong and the other faint - the order read at a glance. */
@Composable
private fun SortDirectionIcon(direction: SortDirection, modifier: Modifier = Modifier) {
    val strong = MaterialTheme.colorScheme.onSurface
    val faint = MaterialTheme.colorScheme.outlineVariant
    val isAscending = direction == SortDirection.ASCENDING
    Box(modifier = modifier.size(18.dp)) {
        Icon(imageVector = SonaIcons.SortUp, contentDescription = null, tint = if (isAscending) strong else faint, modifier = Modifier.matchParentSize())
        Icon(imageVector = SonaIcons.SortDown, contentDescription = null, tint = if (isAscending) faint else strong, modifier = Modifier.matchParentSize())
    }
}
