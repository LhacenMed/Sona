/*
 * Adapted from AB Download Manager's Android browser - BrowserUi and its bookmark sheets - Copyright AB Download Manager
 * contributors, licensed under the Apache License, Version 2.0: https://www.apache.org/licenses/LICENSE-2.0
 */
package com.lhacenmed.sona.feature.download.browser

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SonaIconButton
import com.lhacenmed.sona.core.designsystem.component.SonaIconButtonGroup
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.copyToClipboard
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.component.iconButton
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaBottomSheet
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaBottomSheetScope
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaColumnBottomSheet
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaOptionRow
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.theme.ContainerEdge
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.model.BrowserBookmark
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.download.DownloadViewModel
import com.lhacenmed.sona.feature.download.LinkSheet
import com.lhacenmed.sona.feature.download.R

/**
 * A browser of Sona's own - AB Download Manager's, in Sona's look - for the sites whose download buttons hand a file to the
 * browser rather than showing its link: tabs, the page's own windows opening as tabs, its videos playing - full screen too -
 * and its downloads caught as they start and opened in Sona's download sheet over the page, sent as the page asked for them.
 * Its controls are at the bottom, in reach: the address, then back, forward and reload, the tabs and the menu. A link held
 * opens its own menu. Opened at [page], or with no tab, ready for an address.
 */
data class BrowserScreen(val page: String? = null) : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val downloads: DownloadViewModel = hiltViewModel()
        val browser: BrowserViewModel = hiltViewModel()
        val linkSheet by downloads.linkSheet.collectAsStateWithLifecycle()
        val bookmarks by browser.bookmarks.collectAsStateWithLifecycle()
        var heldLink by remember { mutableStateOf<HeldLink?>(null) }
        var isMenuOpen by remember { mutableStateOf(false) }
        var areTabsOpen by remember { mutableStateOf(false) }
        var areBookmarksOpen by remember { mutableStateOf(false) }
        var editedBookmark by remember { mutableStateOf<EditedBookmark?>(null) }
        val tabs = remember { BrowserTabs(onDownload = { downloads.open(it.url, it.headers) }, onLinkHeld = { heldLink = it }) }
        DisposableEffect(tabs) { onDispose(tabs::releaseAll) }
        var hasOpenedPage by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (!hasOpenedPage) page?.let { tabs.newTab(tabs.pageFor(it)) }
            hasOpenedPage = true
        }
        val tab = tabs.activeTab
        val openPage: (String) -> Unit = { typed -> tabs.pageFor(typed).let { url -> tab?.load(url) ?: tabs.newTab(url) } }

        // The last declared answers first: a page's full screen, then the page before, then the tab itself.
        BackHandler(tabs.tabs.size > 1) { tab?.let(tabs::closeTab) }
        BackHandler(tab?.canGoBack == true) { tab?.goBack() }

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(
                title = stringResource(R.string.download_browser),
                titleLeading = { Icon(SonaIcons.Language, contentDescription = null) },
                onNavigateBack = navigator::back,
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (tab != null) {
                    key(tab.id) {
                        AndroidView(factory = { tabs.webViewOf(tab, it) }, modifier = Modifier.fillMaxSize())
                        DisposableEffect(tab) { onDispose { tabs.pause(tab) } }
                    }
                } else {
                    EmptyPage(
                        onPasteAndGo = { clipboardText(context)?.let(openPage) },
                        onOpenBookmarks = { areBookmarksOpen = true },
                    )
                }
            }
            AddressBar(
                tab = tab,
                tabCount = tabs.tabs.size,
                onOpenPage = openPage,
                onOpenTabs = { areTabsOpen = true },
                onOpenMenu = { isMenuOpen = true },
            )
        }

        tabs.fullScreen?.let { shown ->
            BackHandler(onBack = shown.onExit)
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(factory = { shown.view }, modifier = Modifier.fillMaxSize())
            }
        }

        if (areTabsOpen) {
            TabsSheet(
                tabs = tabs,
                onOpenNew = { typed -> tabs.newTab(typed?.let(tabs::pageFor)) },
                onDismiss = { areTabsOpen = false },
            )
        }
        if (isMenuOpen) {
            MenuSheet(
                tab = tab,
                isBookmarked = tab?.url?.let(browser::isBookmarked) == true,
                onNewTab = { tabs.newTab(url = null) },
                onOpenBookmarks = { areBookmarksOpen = true },
                onAddBookmark = { url -> editedBookmark = EditedBookmark(BrowserBookmark(url, tab?.title.orEmpty()), replacing = null) },
                onRemoveBookmark = browser::remove,
                onCloseTab = { tab?.let(tabs::closeTab) },
                onDismiss = { isMenuOpen = false },
            )
        }
        heldLink?.let { held ->
            HeldLinkSheet(
                link = held.url,
                isBookmarked = browser.isBookmarked(held.url),
                onOpenInNewTab = { tabs.newTab(held.url, switch = true, openedBy = held.tab.id) },
                onOpenInBackground = { tabs.newTab(held.url, switch = false, openedBy = held.tab.id) },
                onShare = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, held.url), null))
                },
                onDownload = { tabs.download(held.url, userAgent = held.tab.webView?.settings?.userAgentString, page = held.tab.url) },
                onAddBookmark = { editedBookmark = EditedBookmark(BrowserBookmark(held.url, ""), replacing = null) },
                onRemoveBookmark = { browser.remove(held.url) },
                onDismiss = { heldLink = null },
            )
        }
        if (areBookmarksOpen) {
            BookmarksSheet(
                bookmarks = bookmarks,
                onOpen = { bookmark ->
                    areBookmarksOpen = false
                    openPage(bookmark.url)
                },
                onEdit = { editedBookmark = EditedBookmark(it, replacing = it) },
                onAdd = { editedBookmark = EditedBookmark(BrowserBookmark("", ""), replacing = null) },
                onRemove = { browser.remove(it.url) },
                onDismiss = { areBookmarksOpen = false },
            )
        }
        editedBookmark?.let { edited ->
            BookmarkDialog(
                edited = edited,
                onSave = { bookmark ->
                    browser.save(bookmark, edited.replacing)
                    editedBookmark = null
                },
                onDismiss = { editedBookmark = null },
            )
        }

        linkSheet?.let { LinkSheet(it, downloads) }
    }
}

/** A bookmark being written: [initial] as it starts, in [replacing]'s place where one is edited. */
private class EditedBookmark(val initial: BrowserBookmark, val replacing: BrowserBookmark?)

/** What the clipboard holds as text - null with nothing. */
private fun clipboardText(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)
        ?.toString()?.trim()?.takeIf(String::isNotEmpty)

/** With no tab open: an address from the clipboard, or a bookmark. */
@Composable
private fun EmptyPage(onPasteAndGo: () -> Unit, onOpenBookmarks: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.browser_no_tab_open), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        EmptyPageButton(SonaIcons.ContentPaste, stringResource(R.string.browser_paste_and_go), onPasteAndGo)
        EmptyPageButton(SonaIcons.Bookmark, stringResource(R.string.browser_bookmarks), onOpenBookmarks)
    }
}

@Composable
private fun EmptyPageButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shapes = buttonPressShapes(), contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Text(label, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
    }
}

/**
 * The browser's controls, under the page: how far it has loaded, the address - [tab]'s, as its title until pressed - then
 * back, forward, and reload or stop; how many tabs are open, which opens them; and the menu.
 */
@Composable
private fun AddressBar(
    tab: BrowserTab?,
    tabCount: Int,
    onOpenPage: (String) -> Unit,
    onOpenTabs: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    val progress = tab?.progress
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface).navigationBarsPadding().imePadding()) {
        HorizontalDivider()
        // Held in place whether or not a page is loading, so the bar never moves as one starts.
        LinearProgressIndicator(
            progress = { progress ?: 0f },
            color = if (progress != null) MaterialTheme.colorScheme.primary else Color.Transparent,
            trackColor = Color.Transparent,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(2.dp),
        )
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            AddressField(tab = tab, onOpenPage = onOpenPage)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                val backLabel = stringResource(R.string.browser_back)
                val forwardLabel = stringResource(R.string.browser_forward)
                val reloadLabel = stringResource(if (progress != null) R.string.browser_stop else R.string.download_browser_reload)
                SonaIconButtonGroup {
                    iconButton(SonaIcons.ArrowBack, backLabel, { tab?.goBack() }, enabled = tab?.canGoBack == true)
                    iconButton(SonaIcons.ArrowForward, forwardLabel, { tab?.goForward() }, enabled = tab?.canGoForward == true)
                    if (tab != null) {
                        iconButton(if (progress != null) SonaIcons.Close else SonaIcons.Refresh, reloadLabel, {
                            if (progress != null) tab.stopLoading() else tab.reload()
                        })
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = onOpenTabs,
                    shapes = buttonPressShapes(),
                    contentPadding = ButtonDefaults.SmallContentPadding,
                    modifier = Modifier.widthIn(min = 48.dp),
                ) {
                    Text("$tabCount", fontWeight = FontWeight.Bold, maxLines = 1)
                }
                SonaIconButton(onClick = onOpenMenu, icon = SonaIcons.MoreVert, contentDescription = stringResource(R.string.browser_menu))
            }
        }
    }
}

/**
 * The address: [tab]'s page - its icon, its title, and a lock for a secure one - pressed to write another, which opens as it is
 * gone to: an address, or a search. Back, or leaving it, goes back to the page.
 */
@Composable
private fun AddressField(tab: BrowserTab?, onOpenPage: (String) -> Unit) {
    val context = LocalContext.current
    val url = tab?.url.orEmpty()
    var isEditing by remember { mutableStateOf(false) }
    BackHandler(enabled = isEditing) { isEditing = false }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isFocused) { isEditing = isFocused }
    val colors = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    if (isEditing) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        var text by remember { mutableStateOf(url) }
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.download_browser_hint)) },
            singleLine = true,
            shape = CircleShape,
            colors = colors,
            interactionSource = interactionSource,
            trailingIcon = {
                Row {
                    SonaIconButton(onClick = { clipboardText(context)?.let { text = it } }, icon = SonaIcons.ContentPaste, contentDescription = stringResource(R.string.browser_paste))
                    // Empties the field, and once empty, leaves it.
                    SonaIconButton(
                        onClick = { if (text.isNotEmpty()) text = "" else isEditing = false },
                        icon = SonaIcons.Close,
                        contentDescription = stringResource(R.string.browser_clear),
                        edge = ContainerEdge.End,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                isEditing = false
                if (text.isNotBlank()) onOpenPage(text)
            }),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TextFieldDefaults.MinHeight)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                .clip(CircleShape)
                .clickable { isEditing = true }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            PageIcon(tab)
            Text(
                text = tab?.title ?: tab?.url ?: stringResource(R.string.download_browser_hint),
                color = if (tab == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 16.dp),
            )
            if (url.startsWith("https://")) {
                Icon(
                    SonaIcons.Lock,
                    contentDescription = stringResource(R.string.browser_secure),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp).size(20.dp),
                )
            }
        }
    }
}

/** [tab]'s site's icon - the globe while it has none. */
@Composable
private fun PageIcon(tab: BrowserTab?) {
    val icon = tab?.icon
    val bitmap = remember(icon) { icon?.asImageBitmap() }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(24.dp))
    } else {
        Icon(SonaIcons.Language, contentDescription = null, modifier = Modifier.size(24.dp))
    }
}

/** A sheet's [title], with [actions] at its end. */
@Composable
private fun SheetHeader(title: String, actions: ButtonGroupScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        SonaIconButtonGroup(content = actions)
    }
}

/** Every open tab - the one in front marked - each opened by a tap and closed by its own button; a new one, blank or pasted. */
@Composable
private fun TabsSheet(tabs: BrowserTabs, onOpenNew: (String?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pasteLabel = stringResource(R.string.browser_paste)
    val newLabel = stringResource(R.string.browser_new_tab)
    val closeLabel = stringResource(R.string.browser_close)
    SonaColumnBottomSheet(
        onDismissRequest = onDismiss,
        header = {
            SheetHeader(stringResource(R.string.browser_tabs)) {
                iconButton(SonaIcons.ContentPaste, pasteLabel, {
                    onOpenNew(clipboardText(context))
                    onDismiss()
                })
                iconButton(SonaIcons.Add, newLabel, {
                    onOpenNew(null)
                    onDismiss()
                })
                iconButton(SonaIcons.Close, closeLabel, onDismiss)
            }
        },
    ) {
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(tabs.tabs, key = BrowserTab::id) { item ->
                ListItem(
                    headlineContent = { Text(item.title ?: item.url ?: stringResource(R.string.browser_blank), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { PageIcon(item) },
                    trailingContent = {
                        SonaIconButton(onClick = { tabs.closeTab(item) }, icon = SonaIcons.Close, contentDescription = stringResource(R.string.browser_close_tab))
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (item == tabs.activeTab) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    ),
                    modifier = Modifier.clickable {
                        tabs.switchTo(item)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/** The browser's menu, named for [tab]'s page: a new tab; the bookmarks, and this page among them or not; closing the tab. */
@Composable
private fun MenuSheet(
    tab: BrowserTab?,
    isBookmarked: Boolean,
    onNewTab: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onAddBookmark: (String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onCloseTab: () -> Unit,
    onDismiss: () -> Unit,
) {
    val url = tab?.url
    SonaBottomSheet(title = tab?.title ?: stringResource(R.string.browser_menu), onDismissRequest = onDismiss) {
        MenuRow(SonaIcons.Add, stringResource(R.string.browser_new_tab), onNewTab)
        HorizontalDivider()
        MenuRow(SonaIcons.Bookmark, stringResource(R.string.browser_bookmarks), onOpenBookmarks)
        if (url != null) BookmarkRow(url, isBookmarked, onAddBookmark, onRemoveBookmark)
        if (tab != null) {
            HorizontalDivider()
            MenuRow(SonaIcons.Close, stringResource(R.string.browser_close_tab), onCloseTab)
        }
    }
}

/** What a link held in a page can do: open in a tab, in front or behind; be shared, copied, downloaded, or bookmarked. */
@Composable
private fun HeldLinkSheet(
    link: String,
    isBookmarked: Boolean,
    onOpenInNewTab: () -> Unit,
    onOpenInBackground: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onAddBookmark: () -> Unit,
    onRemoveBookmark: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val copiedLabel = stringResource(R.string.browser_link_copied)
    SonaBottomSheet(title = link, onDismissRequest = onDismiss) {
        MenuRow(SonaIcons.OpenInNew, stringResource(R.string.browser_open_in_new_tab), onOpenInNewTab)
        MenuRow(SonaIcons.Tab, stringResource(R.string.browser_open_in_background), onOpenInBackground)
        MenuRow(SonaIcons.Share, stringResource(R.string.browser_share), onShare)
        MenuRow(SonaIcons.ContentCopy, stringResource(R.string.browser_copy)) { context.copyToClipboard(link, copiedLabel) }
        MenuRow(SonaIcons.Download, stringResource(R.string.browser_download), onDownload)
        BookmarkRow(link, isBookmarked, { onAddBookmark() }, { onRemoveBookmark() })
    }
}

/** A menu's row: it does [onClick], and the sheet slides away. */
@Composable
private fun SonaBottomSheetScope.MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    SonaOptionRow(label = label, icon = icon, onClick = {
        onClick()
        dismiss()
    })
}

/** Adds [url] to the bookmarks, or takes it out where [isBookmarked]. */
@Composable
private fun SonaBottomSheetScope.BookmarkRow(url: String, isBookmarked: Boolean, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    if (isBookmarked) {
        MenuRow(SonaIcons.Delete, stringResource(R.string.browser_remove_from_bookmarks)) { onRemove(url) }
    } else {
        MenuRow(SonaIcons.Bookmark, stringResource(R.string.browser_add_to_bookmarks)) { onAdd(url) }
    }
}

/** Every bookmark - opened by a tap, edited by a long press, taken out by its own button - and a new one. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarksSheet(
    bookmarks: List<BrowserBookmark>,
    onOpen: (BrowserBookmark) -> Unit,
    onEdit: (BrowserBookmark) -> Unit,
    onAdd: () -> Unit,
    onRemove: (BrowserBookmark) -> Unit,
    onDismiss: () -> Unit,
) {
    val addLabel = stringResource(R.string.browser_add_bookmark)
    val closeLabel = stringResource(R.string.browser_close)
    SonaColumnBottomSheet(
        onDismissRequest = onDismiss,
        header = {
            SheetHeader(stringResource(R.string.browser_bookmarks)) {
                iconButton(SonaIcons.Add, addLabel, onAdd)
                iconButton(SonaIcons.Close, closeLabel, onDismiss)
            }
        },
    ) {
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(bookmarks, key = BrowserBookmark::url) { bookmark ->
                ListItem(
                    headlineContent = { Text(bookmark.title.ifEmpty { bookmark.url }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(bookmark.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(SonaIcons.Language, contentDescription = null) },
                    trailingContent = {
                        SonaIconButton(onClick = { onRemove(bookmark) }, icon = SonaIcons.Delete, contentDescription = stringResource(R.string.browser_remove_from_bookmarks))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.combinedClickable(onLongClick = { onEdit(bookmark) }, onClick = { onOpen(bookmark) }),
                )
            }
        }
    }
}

/** A bookmark written: its title and its address, both needed to save it. */
@Composable
private fun BookmarkDialog(edited: EditedBookmark, onSave: (BrowserBookmark) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(edited.initial.title) }
    var url by remember { mutableStateOf(edited.initial.url) }
    val cancelLabel = stringResource(R.string.download_cancel)
    val saveLabel = stringResource(R.string.browser_save)
    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(if (edited.replacing != null) R.string.browser_edit_bookmark else R.string.browser_add_bookmark),
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = saveLabel, enabled = title.isNotBlank() && url.isNotBlank(), onClick = {
                onSave(BrowserBookmark(url.trim(), title.trim()))
            })
        },
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.browser_bookmark_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(R.string.browser_bookmark_url)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}
