package com.lhacenmed.sona.core.designsystem.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.lhacenmed.sona.core.designsystem.R

/**
 * One thing a top app bar can do. Whether it is drawn as an icon or as a row in the overflow menu is
 * the bar's decision rather than the caller's - a screen declares what it offers, and the bar works
 * out how much of it fits.
 */
@Immutable
data class TopBarAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** What the bar shows while a list has rows selected: how many, and what can be done with them. */
@Immutable
data class TopBarSelection(
    val count: Int,
    val actions: List<TopBarAction>,
    val onDismiss: () -> Unit,
)

/** How many actions are drawn as icons before the rest collapse into the overflow menu. */
private const val MAX_VISIBLE_ACTIONS = 2

private const val ENTER_SCALE = 0.92f
private const val EXIT_SCALE = 0.92f
private const val TRANSITION_MILLIS = 180

/**
 * The only top app bar in the app.
 *
 * It has two modes - an ordinary bar, and a context bar for when a list has a selection - and it
 * cross-fades between them with a slight scale, so the change reads as one bar changing its mind
 * rather than two bars swapping places. Both modes are a `TopAppBar` of identical height, so nothing
 * around the bar moves while it transitions.
 *
 * Taking [actions] as data rather than as a slot is what lets the bar overflow: it draws the first
 * few as icons and folds the remainder into a dropdown, the way a platform action bar does. A screen
 * that grows a sixth action needs no layout work to accommodate it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonaTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    actions: List<TopBarAction> = emptyList(),
    selection: TopBarSelection? = null,
) {
    // Back ends the selection rather than the screen, which is what a context bar is expected to
    // do. Handling it here means no screen can adopt the context bar and forget the gesture.
    BackHandler(enabled = selection != null) { selection?.onDismiss?.invoke() }

    AnimatedContent(
        targetState = selection,
        modifier = modifier,
        // Keyed on the *mode*, not on the selection value: selecting another row changes the count
        // and must simply re-render the context bar, not animate a fresh one in over it.
        contentKey = { it != null },
        transitionSpec = {
            val spec = tween<Float>(durationMillis = TRANSITION_MILLIS)
            (fadeIn(spec) + scaleIn(spec, initialScale = ENTER_SCALE)) togetherWith
                (fadeOut(spec) + scaleOut(spec, targetScale = EXIT_SCALE)) using
                // Both modes are the same height, so there is no size to animate - and not clipping
                // leaves the outgoing bar whole while it scales.
                SizeTransform(clip = false)
        },
        label = "topAppBarMode",
    ) { activeSelection ->
        if (activeSelection == null) {
            TopAppBar(
                title = { BarTitle(title = title, subtitle = subtitle) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.top_bar_back),
                            )
                        }
                    }
                },
                actions = { BarActions(actions = actions) },
            )
        } else {
            TopAppBar(
                title = {
                    BarTitle(
                        title = stringResource(R.string.top_bar_selected_count, activeSelection.count),
                        subtitle = null,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = activeSelection.onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.top_bar_clear_selection),
                        )
                    }
                },
                actions = { BarActions(actions = activeSelection.actions) },
                // Tinted, so it is obvious at a glance that the app is in a different mode.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun BarTitle(title: String, subtitle: String?) {
    if (subtitle == null) {
        Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    Column {
        Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BarActions(actions: List<TopBarAction>) {
    actions.take(MAX_VISIBLE_ACTIONS).forEach { action ->
        IconButton(onClick = action.onClick) {
            Icon(imageVector = action.icon, contentDescription = action.label)
        }
    }

    val overflowed = actions.drop(MAX_VISIBLE_ACTIONS)
    if (overflowed.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.top_bar_more_actions),
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        overflowed.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
                leadingIcon = { Icon(imageVector = action.icon, contentDescription = null) },
                onClick = {
                    expanded = false
                    action.onClick()
                },
            )
        }
    }
}
