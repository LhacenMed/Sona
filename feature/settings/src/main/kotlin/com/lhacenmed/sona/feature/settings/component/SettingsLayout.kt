package com.lhacenmed.sona.feature.settings.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.WindowTouchBlocker
import com.lhacenmed.sona.core.designsystem.component.screen.screenList
import com.lhacenmed.sona.core.designsystem.component.section.ColumnSection
import com.lhacenmed.sona.core.navigation.LocalScreenEntered
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** How long an arrived-at row's ripple is held before it fades - long enough to be seen spreading. */
private const val HighlightHoldMillis = 500L

/**
 * The longest a screen waits for its enter animation to report finishing before scrolling anyway - so a
 * device that never reports it cannot leave the screen's touches blocked.
 */
private const val MaxEnterWaitMillis = 1_000L

/**
 * The row a [SettingsList] opens scrolled to - by its [key], as [settingsScrollTarget] marks it - and
 * where that row and the list are laid out, read once the screen is in.
 */
private class SettingsScrollTarget(val key: Any) {
    var list: LayoutCoordinates? = null
    var row: LayoutCoordinates? = null

    /** What the row's ripple answers to, as it would a finger's press. */
    val interactionSource = MutableInteractionSource()
}

private val LocalSettingsScrollTarget = staticCompositionLocalOf<SettingsScrollTarget?> { null }

/** What scrolls the [SettingsList] a [SettingsSection] is in - which its heading stays pinned by. */
private val LocalSettingsScrollState = staticCompositionLocalOf<ScrollState> { error("A SettingsSection belongs in a SettingsList") }

/**
 * The body every settings screen has: its rows, in order, scrolling as one.
 *
 * A plain scrolling [Column] rather than a `LazyColumn` - a settings screen's rows are a fixed,
 * short, hand-written list, and a lazy container would allocate scroll state and defer composition
 * for rows that are all going to exist anyway.
 *
 * Given [scrollTo], it opens scrolled to the row marked with that key - see [settingsScrollTarget]: once
 * the screen has come in, it glides there, bringing the row to the middle, and the row ripples as if
 * pressed so the eye lands on it. Touches are kept from the screen until it arrives, so a stray one
 * cannot stop it short. Once only: a screen recreated after it arrived stays where it is.
 */
@Composable
fun SettingsList(scrollTo: Any? = null, content: @Composable ColumnScope.() -> Unit) {
    val scrollState = rememberScrollState()
    val target = remember(scrollTo) { scrollTo?.let(::SettingsScrollTarget) }
    var hasArrived by rememberSaveable { mutableStateOf(scrollTo == null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPlaced { target?.list = it }
            .screenList(scrollState)
            .verticalScroll(scrollState)
            .padding(bottom = LocalBottomContentPadding.current),
    ) {
        CompositionLocalProvider(
            LocalSettingsScrollTarget provides target,
            LocalSettingsScrollState provides scrollState,
        ) { content() }
    }

    if (target == null) return
    // Touches come back the moment it arrives; the row's ripple needs none of them held.
    if (!hasArrived) WindowTouchBlocker()
    val hasEntered = LocalScreenEntered.current
    val scrollSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(target) {
        if (hasArrived) return@LaunchedEffect
        withTimeoutOrNull(MaxEnterWaitMillis) { snapshotFlow { hasEntered.value }.first { it } }
        val list = target.list
        val row = target.row
        if (list == null || row == null || !list.isAttached || !row.isAttached) {
            hasArrived = true
            return@LaunchedEffect
        }
        val rowTop = list.localPositionOf(row, Offset.Zero).y
        scrollState.animateScrollBy(rowTop - (list.size.height - row.size.height) / 2f, scrollSpec)
        hasArrived = true
        val press = PressInteraction.Press(Offset(row.size.width / 2f, row.size.height / 2f))
        target.interactionSource.emit(press)
        delay(HighlightHoldMillis)
        target.interactionSource.emit(PressInteraction.Release(press))
    }
}

/**
 * Marks this row as the one its [SettingsList] opens scrolled to while it is asked for [key], and draws
 * the ripple it arrives with. Anything else, it leaves as it is.
 */
@Composable
fun Modifier.settingsScrollTarget(key: Any): Modifier {
    val target = LocalSettingsScrollTarget.current
    if (target?.key != key) return this
    return onPlaced { target.row = it }.indication(target.interactionSource, ripple())
}

/**
 * A named group of related settings, under the app's one section heading - see [ColumnSection].
 *
 * The rule the screens follow is one a reader can check at a glance: a screen either has no sections
 * at all, or every row belongs to one. Half-sectioned screens are what make a settings page feel
 * arbitrary.
 *
 * Its heading stays at the top of the list while its rows scroll under it, and folds the group down to
 * itself at a press, so the groups under it are one press from sight. What acts on the whole group sits on
 * that heading - its [actions].
 */
@Composable
fun ColumnScope.SettingsSection(
    title: String,
    actions: List<TopBarAction> = emptyList(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ColumnSection(
        title = title,
        scrollState = LocalSettingsScrollState.current,
        actions = actions,
        content = content,
    )
}

/**
 * A [SettingsList] for rows that are data rather than written out - licenses, releases, commits: as many
 * as there are, so only those on screen are composed. The same rows, the same way down the screen.
 * [listState] is the screen's to hold when its sections collapse - see `rememberSectionListState`.
 */
@Composable
fun SettingsLazyList(
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().screenList(listState),
        contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
        content = content,
    )
}

/** The line between two [SettingsSection]s. */
@Composable
fun SettingsSectionDivider() {
    HorizontalDivider()
}
