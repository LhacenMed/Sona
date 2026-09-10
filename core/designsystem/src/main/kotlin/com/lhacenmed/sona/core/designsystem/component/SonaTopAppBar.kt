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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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

/** What the bar shows while a screen is being searched: the query, in place of the title. */
@Immutable
data class TopBarSearch(
    val query: String,
    val onQueryChange: (String) -> Unit,
    val onClose: () -> Unit,
)

/** How many actions are drawn as icons before the rest collapse into the overflow menu. */
private const val MAX_VISIBLE_ACTIONS = 2

private const val ENTER_SCALE = 0.92f
private const val EXIT_SCALE = 0.92f
private const val TRANSITION_MILLIS = 180

/**
 * What the bar is currently showing.
 *
 * The mode carries its own data rather than being an enum read alongside the parameters, because
 * `AnimatedContent` keeps rendering the outgoing mode for the length of the transition - and by then
 * the parameter that produced it is already null. Holding the data here means the bar that is
 * leaving still has everything it needs to draw itself on the way out.
 */
private sealed interface BarContent {
    data class Browsing(
        val title: String,
        val subtitle: String?,
        val actions: List<TopBarAction>,
    ) : BarContent

    data class Searching(val search: TopBarSearch) : BarContent

    data class Selecting(val selection: TopBarSelection) : BarContent
}

/**
 * The only top app bar in the app.
 *
 * It has three modes - ordinary, searching, and a context bar for when a list has a selection - and
 * cross-fades between them with a slight scale, so a change reads as one bar changing its mind
 * rather than bars swapping places. Every mode is a `TopAppBar` of identical height, so nothing
 * around the bar moves while it transitions.
 *
 * Searching replaces the title rather than sitting beside it, which is what makes it feel like the
 * bar became the search rather than grew one. A selection outranks a search: if rows get picked
 * while searching, the context bar is what the user needs to see.
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
    search: TopBarSearch? = null,
) {
    val content: BarContent = when {
        selection != null -> BarContent.Selecting(selection)
        search != null -> BarContent.Searching(search)
        else -> BarContent.Browsing(title, subtitle, actions)
    }

    // Back leaves the mode rather than the screen, which is what both a context bar and a search
    // field are expected to do. Handling it here means no screen can adopt either and forget it.
    BackHandler(enabled = selection != null || search != null) {
        selection?.onDismiss?.invoke() ?: search?.onClose?.invoke()
    }

    AnimatedContent(
        targetState = content,
        modifier = modifier,
        // Keyed on the *mode*, not the value: typing a letter or picking another row must re-render
        // the bar it is already in, not animate a fresh one in over it.
        contentKey = { it::class },
        transitionSpec = {
            val spec = tween<Float>(durationMillis = TRANSITION_MILLIS)
            (fadeIn(spec) + scaleIn(spec, initialScale = ENTER_SCALE)) togetherWith
                (fadeOut(spec) + scaleOut(spec, targetScale = EXIT_SCALE)) using
                // Every mode is the same height, so there is no size to animate - and not clipping
                // leaves the outgoing bar whole while it scales.
                SizeTransform(clip = false)
        },
        label = "topAppBarMode",
    ) { activeContent ->
        when (activeContent) {
            is BarContent.Browsing -> TopAppBar(
                title = { BarTitle(title = activeContent.title, subtitle = activeContent.subtitle) },
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
                actions = { BarActions(actions = activeContent.actions) },
            )

            is BarContent.Searching -> TopAppBar(
                title = { SearchField(search = activeContent.search) },
                navigationIcon = {
                    IconButton(onClick = activeContent.search.onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.top_bar_close_search),
                        )
                    }
                },
            )

            is BarContent.Selecting -> TopAppBar(
                title = {
                    BarTitle(
                        title = stringResource(
                            R.string.top_bar_selected_count,
                            activeContent.selection.count,
                        ),
                        subtitle = null,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = activeContent.selection.onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.top_bar_clear_selection),
                        )
                    }
                },
                actions = { BarActions(actions = activeContent.selection.actions) },
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
private fun SearchField(search: TopBarSearch) {
    val focusRequester = remember { FocusRequester() }

    // Opening the search is the whole point of the tap, so it takes the keyboard with it rather
    // than asking for a second tap to start typing.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    TextField(
        value = search.query,
        onValueChange = search.onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        placeholder = { Text(stringResource(R.string.top_bar_search_hint)) },
        singleLine = true,
        // Undecorated, so it reads as the bar's title turned editable rather than a field dropped
        // into the bar.
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
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
