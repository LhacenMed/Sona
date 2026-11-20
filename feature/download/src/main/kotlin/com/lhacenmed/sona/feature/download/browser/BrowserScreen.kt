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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
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
import com.lhacenmed.sona.core.designsystem.component.animateProgressAsState
import com.lhacenmed.sona.core.designsystem.component.SonaIconButtonGroup
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.copyToClipboard
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.component.iconButton
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaBottomSheet
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.lhacenmed.sona.core.designsystem.component.sheet.SheetTitle
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
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.clearText
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material3.Switch
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.style.TextAlign
import com.lhacenmed.sona.feature.download.linkIn
import com.lhacenmed.sona.core.designsystem.component.toast
import java.util.Locale
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.ui.res.pluralStringResource
import android.net.Uri
import com.lhacenmed.sona.feature.download.browser.shields.Shields
import com.lhacenmed.sona.feature.download.settings.BrowserSettingsScreen
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.lhacenmed.sona.core.designsystem.theme.iconButtonPressShapes
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.unit.IntSize

/**
 * A browser of Sona's own - AB Download Manager's, in Sona's look - for the sites whose download buttons hand a file to the
 * browser rather than showing its link: tabs, the page's own windows opening as tabs, its videos playing - full screen too -
 * and its downloads caught as they start and opened in Sona's download sheet over the page, sent as the page asked for them.
 * Its controls are at the bottom, in reach: the address, then back, forward and reload, the tabs and the menu. A link held
 * opens its own menu. Opened at [page], or with no tab, ready for an address.
 *
 * It is also where downloads are signed in for: the user signs in on a site as on any browser, and goes back - every download
 * from that site is signed in as the browser is, and each that failed for want of it tries again as the browser is left. See
 * [BrowserCookies].
 */
data class BrowserScreen(val page: String? = null) : Screen {

    // Its own controls are at its foot, where the mini player would stand over them.
    override val hasPlayer: Boolean get() = false

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
        var areShieldsOpen by remember { mutableStateOf(false) }
        var isEditingAddress by remember { mutableStateOf(false) }
        val shieldsPreferences by browser.shieldsPreferences.collectAsStateWithLifecycle()
        var editedBookmark by remember { mutableStateOf<EditedBookmark?>(null) }
        val tabs = remember {
            BrowserTabs(
                onDownload = { downloads.open(it.url, it.headers) },
                onLinkHeld = { heldLink = it },
                searchEngine = { browser.searchEngine.value },
                shields = browser.shields,
                cookies = browser.cookies,
            )
        }
        // The Shields' filter lists, read into memory while the browser is open - and let go as it closes.
        DisposableEffect(browser.shields) {
            browser.shields.lists.open()
            onDispose(browser.shields.lists::close)
        }
        // The page's own tool in the address bar's place - finding in it, or zooming it - put away as another tab comes in front.
        var pageTool by remember { mutableStateOf<PageTool?>(null) }
        // A link copied here - from a page, or a link held - is opened to download, as one shared with Sona is.
        DisposableEffect(context) {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            val listener = ClipboardManager.OnPrimaryClipChangedListener { linkIn(clipboardText(context))?.let(downloads::open) }
            clipboard?.addPrimaryClipChangedListener(listener)
            onDispose { clipboard?.removePrimaryClipChangedListener(listener) }
        }
        DisposableEffect(tabs) { onDispose(tabs::releaseAll) }
        // Signed in here, the downloads that waited on it go on as the browser is left.
        DisposableEffect(browser) { onDispose(browser::onLeft) }
        var hasOpenedPage by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (!hasOpenedPage) page?.let { tabs.newTab(tabs.pageFor(it)) }
            hasOpenedPage = true
        }
        val tab = tabs.activeTab
        LaunchedEffect(tab) { pageTool = null }
        val colors = MaterialTheme.colorScheme
        val readerStyle = ReaderStyle(background = colors.surface.cssHex(), text = colors.onSurface.cssHex(), link = colors.primary.cssHex())
        val openPage: (String) -> Unit = { typed -> tabs.pageFor(typed).let { url -> tab?.load(url) ?: tabs.newTab(url) } }

        // The last declared answers first: a page's full screen, then the page before, then the tab itself.
        BackHandler(tabs.tabs.size > 1) { tab?.let(tabs::closeTab) }
        BackHandler(tab?.canGoBack == true) { tab?.goBack() }

        // Brave's layout: back and the address at the top, the page, and the page's moves, home, bookmark, tabs and menu at the
        // foot - put away while an address is written, the keyboard in its place.
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            TopBar(
                onExit = navigator::close,
                tab = tab,
                pageTool = pageTool,
                onClosePageTool = { pageTool = null },
                isEditing = isEditingAddress,
                onEditingChange = { isEditingAddress = it },
                onOpenPage = openPage,
                isShieldsUp = browser.shields.isUp(tab?.url?.let(Uri::parse)?.host, shieldsPreferences),
                onOpenShields = { areShieldsOpen = true },
            )
            // Clipped to its own place: a web view's first frames, drawn before its page is, would otherwise spill over the bar
            // above it - blank until the page paints.
            val pageColor = MaterialTheme.colorScheme.surface.toArgb()
            Box(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                if (tab != null) {
                    key(tab.id) {
                        // On the screen's own colour until the page paints its, rather than a blank of the web view's.
                        AndroidView(factory = { tabs.webViewOf(tab, it) }, update = { it.setBackgroundColor(pageColor) }, modifier = Modifier.fillMaxSize())
                        DisposableEffect(tab) { onDispose { tabs.pause(tab) } }
                    }
                }
                // The start page - home - over a tab with no page, as with none.
                if (tab?.url == null) {
                    EmptyPage(
                        onPasteAndGo = { clipboardText(context)?.let(openPage) },
                        onOpenBookmarks = { areBookmarksOpen = true },
                    )
                }
            }
            AnimatedVisibility(
                visible = !isEditingAddress,
                enter = slideInVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } +
                    expandVertically(MaterialTheme.motionScheme.fastSpatialSpec(), expandFrom = Alignment.Top),
                exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } +
                    shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec(), shrinkTowards = Alignment.Top),
            ) {
                val url = tab?.url
                BottomBar(
                    tab = tab,
                    tabCount = tabs.tabs.size,
                    isBookmarked = url?.let(browser::isBookmarked) == true,
                    onToggleBookmark = {
                        if (url == null) return@BottomBar
                        if (browser.isBookmarked(url)) browser.remove(url)
                        else editedBookmark = EditedBookmark(BrowserBookmark(url, tab?.title.orEmpty()), replacing = null)
                    },
                    onTabs = { areTabsOpen = true },
                    onMenu = { isMenuOpen = true },
                )
            }
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
                onNewTab = { tabs.newTab(url = null) },
                onOpenBookmarks = { areBookmarksOpen = true },
                onCloseTab = { tab?.let(tabs::closeTab) },
                onFindInPage = { pageTool = PageTool.FIND },
                onZoom = { pageTool = PageTool.ZOOM },
                onTranslate = { tab?.translate(Locale.getDefault().language) },
                onReadingMode = {
                    val readability = context.assets.open("browser/Readability.js").bufferedReader().use { it.readText() }
                    tab?.showReadingMode(readability, readerStyle, onNone = { context.toast(R.string.browser_reading_mode_unavailable) })
                },
                onDesktopSite = { tab?.setDesktopSite(it) },
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
        if (areShieldsOpen && tab != null) {
            ShieldsSheet(
                tab = tab,
                shields = browser.shields,
                onOpenSettings = { navigator.go(BrowserSettingsScreen) },
                onDismiss = { areShieldsOpen = false },
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

/** The start page - with no tab open, or home: an address from the clipboard, or a bookmark. */
@Composable
private fun EmptyPage(onPasteAndGo: () -> Unit, onOpenBookmarks: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
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
 * Brave's top bar: the address - [tab]'s page, or what is being written in its place - with the Shields at its end; while the
 * page is found in or zoomed, that tool. Under it, how far the page has loaded.
 */
@Composable
private fun TopBar(
    onExit: () -> Unit,
    tab: BrowserTab?,
    pageTool: PageTool?,
    onClosePageTool: () -> Unit,
    isEditing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onOpenPage: (String) -> Unit,
    isShieldsUp: Boolean,
    onOpenShields: () -> Unit,
) {
    val progress = tab?.progress
    val shownProgress by animateProgressAsState(progress ?: 0f)
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
        if (tab != null && pageTool != null) {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = BarHeight).padding(horizontal = 12.dp, vertical = 4.dp)) {
                when (pageTool) {
                    PageTool.FIND -> FindBar(tab, onClose = onClosePageTool)
                    PageTool.ZOOM -> ZoomBar(tab, onClose = onClosePageTool)
                }
            }
        } else {
            // The app bar's own inset before its back button; the field's own, at both ends, once it has the bar to itself.
            val startPadding by animateDpAsState(if (isEditing) 12.dp else 4.dp, MaterialTheme.motionScheme.fastSpatialSpec())
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = BarHeight).padding(start = startPadding, end = 12.dp, top = 4.dp, bottom = 4.dp),
            ) {
                // The app bar's back, leaving the browser whatever page it is on - the system's back goes back through the
                // pages. It gives way to the field as an address is written, the field growing into its place.
                AnimatedVisibility(
                    visible = !isEditing,
                    enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                        expandHorizontally(MaterialTheme.motionScheme.fastSpatialSpec(), expandFrom = Alignment.Start),
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                        shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec(), shrinkTowards = Alignment.Start),
                ) {
                    SonaIconButton(onClick = onExit, icon = SonaIcons.ArrowBack, contentDescription = stringResource(R.string.browser_leave))
                }
                Box(modifier = Modifier.weight(1f)) {
                    AddressField(tab, isEditing, onEditingChange, onOpenPage, isShieldsUp, onOpenShields)
                }
            }
        }
        // Held in place whether or not a page is loading, so the page never moves as one starts.
        LinearProgressIndicator(
            progress = { shownProgress },
            color = if (progress != null) MaterialTheme.colorScheme.primary else Color.Transparent,
            trackColor = Color.Transparent,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(2.dp),
        )
    }
}

/** How tall each of the browser's bars is - the least that holds a touch target, and leaves the page the rest. */
private val BarHeight = 56.dp

/**
 * The address, as Brave's bar shows it: [tab]'s site's icon and address - or, pressed, a field to write another in, opened as
 * it is gone to: an address, or a search - and the Shields' button. Back, or leaving it, goes back to the page.
 */
@Composable
private fun AddressField(
    tab: BrowserTab?,
    isEditing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onOpenPage: (String) -> Unit,
    isShieldsUp: Boolean,
    onOpenShields: () -> Unit,
) {
    val context = LocalContext.current
    val url = tab?.url.orEmpty()
    val focusManager = LocalFocusManager.current
    // Its focus let go of first: the field leaving with it would hand it on to the next button, ringed.
    fun stopEditing() {
        focusManager.clearFocus()
        onEditingChange(false)
    }
    BackHandler(enabled = isEditing, onBack = ::stopEditing)
    val shape = RoundedCornerShape(12.dp)
    // Remembered here, not in the field: its buttons, at the bar's end, write in it too.
    val text = rememberTextFieldState(url, initialSelection = TextRange(0, url.length))
    LaunchedEffect(isEditing) { if (isEditing) text.edit { replace(0, length, url); selection = TextRange(0, url.length) } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (isEditing) Modifier else Modifier.clickable { onEditingChange(true) })
            .padding(start = 14.dp),
    ) {
        Swapped(isEditing) { editing ->
            if (editing) {
                Icon(SonaIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                PageIcon(tab, size = 20.dp)
            }
        }
        Swapped(isEditing, modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) { editing ->
            if (editing) {
                val focusRequester = remember { FocusRequester() }
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()
                var wasFocused by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                // Leaving the field - a tap on the page - is leaving the editing.
                LaunchedEffect(isFocused) {
                    if (isFocused) wasFocused = true else if (wasFocused) onEditingChange(false)
                }
                BasicTextField(
                    state = text,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    interactionSource = interactionSource,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    onKeyboardAction = {
                        stopEditing()
                        text.text.toString().takeIf(String::isNotBlank)?.let(onOpenPage)
                    },
                    decorator = { field ->
                        if (text.text.isEmpty()) AddressText(stringResource(R.string.download_browser_hint), isHint = true)
                        field()
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            } else {
                AddressText(displayAddress(url).ifEmpty { stringResource(R.string.download_browser_hint) }, isHint = url.isEmpty())
            }
        }
        Swapped(isEditing) { editing ->
            if (editing) {
                Row {
                    SonaIconButton(onClick = { clipboardText(context)?.let(text::setTextAndPlaceCursorAtEnd) }, icon = SonaIcons.ContentPaste, contentDescription = stringResource(R.string.browser_paste))
                    // Empties the field, and once empty, leaves it.
                    SonaIconButton(
                        onClick = { if (text.text.isNotEmpty()) text.clearText() else stopEditing() },
                        icon = SonaIcons.Close,
                        contentDescription = stringResource(R.string.browser_clear),
                        edge = ContainerEdge.End,
                    )
                }
            } else if (url.isNotEmpty()) {
                SonaIconButton(
                    onClick = onOpenShields,
                    icon = if (isShieldsUp) SonaIcons.Shield else SonaIcons.ShieldOff,
                    contentDescription = stringResource(R.string.browser_shields),
                    edge = ContainerEdge.End,
                )
            }
        }
    }
}

/**
 * The address field's parts - its icon, its text, its buttons - as the page's address shows them, or as one being written:
 * one faded into the other, its width following, with Material's own quick motion.
 */
@Composable
private fun Swapped(isEditing: Boolean, modifier: Modifier = Modifier, content: @Composable (Boolean) -> Unit) {
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<IntSize>()
    AnimatedContent(
        targetState = isEditing,
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
        transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) using SizeTransform(clip = false) { _, _ -> spatial } },
        label = "addressField",
    ) { editing -> content(editing) }
}

/** The address bar's own text: one line, cut short at its end - a hint dimmer than an address. */
@Composable
private fun AddressText(text: String, isHint: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (isHint) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** An address as a browser's bar shows it: no scheme, no `www.`, no trailing slash. */
private fun displayAddress(url: String): String =
    url.substringAfter("://").removePrefix("www.").removeSuffix("/")

/**
 * The bottom bar, in two groups: the page's own moves - back, forward, reload or stop - at its start; at its end, home - the
 * start page - the page bookmarked or not, the tabs - how many, in a frame - and the menu. What has nothing to act on - no page,
 * no page before or after - stands disabled.
 */
@Composable
private fun BottomBar(
    tab: BrowserTab?,
    tabCount: Int,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
) {
    val hasPage = tab?.url != null
    val isLoading = tab?.progress != null
    // Read here rather than inside the groups: they build their buttons outside composition.
    val backLabel = stringResource(R.string.browser_back)
    val forwardLabel = stringResource(R.string.browser_forward)
    val reloadLabel = stringResource(if (isLoading) R.string.browser_stop else R.string.download_browser_reload)
    val homeLabel = stringResource(R.string.browser_home)
    val bookmarkLabel = stringResource(if (isBookmarked) R.string.browser_remove_from_bookmarks else R.string.browser_add_to_bookmarks)
    val tabsLabel = stringResource(R.string.browser_tabs)
    val menuLabel = stringResource(R.string.browser_menu)
    val frameColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .height(BarHeight)
            .padding(horizontal = 8.dp),
    ) {
        SonaIconButtonGroup {
            iconButton(SonaIcons.ArrowBack, backLabel, { tab?.goBack() }, enabled = tab?.canGoBack == true)
            iconButton(SonaIcons.ArrowForward, forwardLabel, { tab?.goForward() }, enabled = tab?.canGoForward == true)
            iconButton(if (isLoading) SonaIcons.Close else SonaIcons.Refresh, reloadLabel, {
                if (isLoading) tab?.stopLoading() else tab?.reload()
            }, enabled = hasPage)
        }
        Spacer(modifier = Modifier.weight(1f))
        SonaIconButtonGroup {
            iconButton(SonaIcons.Home, homeLabel, { tab?.goHome() }, enabled = hasPage)
            iconButton(if (isBookmarked) SonaIcons.BookmarkAdded else SonaIcons.BookmarkAdd, bookmarkLabel, onToggleBookmark, enabled = hasPage)
            customItem(
                buttonGroupContent = {
                    val interactionSource = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = onTabs,
                        shapes = iconButtonPressShapes(),
                        interactionSource = interactionSource,
                        modifier = Modifier.animateWidth(interactionSource).semantics { contentDescription = tabsLabel },
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(22.dp).border(2.dp, frameColor, RoundedCornerShape(5.dp)),
                        ) {
                            Text(
                                text = if (tabCount > 99) "99+" else "$tabCount",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                },
                menuContent = { menuState ->
                    DropdownMenuItem(text = { Text(tabsLabel) }, onClick = { menuState.dismiss(); onTabs() })
                },
            )
            iconButton(SonaIcons.MoreVert, menuLabel, onMenu)
        }
    }
}

/**
 * Brave's Shields panel for [tab]'s site: up or down on it - the page loaded again as it changes - what they blocked of the
 * page, and the way to their settings for every site.
 */
@Composable
private fun ShieldsSheet(tab: BrowserTab, shields: Shields, onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    val host = tab.url?.let(Uri::parse)?.host ?: return
    val isUp = shields.isUp(host)
    SonaBottomSheet(title = host, onDismissRequest = onDismiss) {
        SonaOptionRow(
            label = stringResource(if (isUp) R.string.browser_shields_up else R.string.browser_shields_down),
            icon = if (isUp) SonaIcons.Shield else SonaIcons.ShieldOff,
            detail = stringResource(if (isUp) R.string.browser_shields_up_summary else R.string.browser_shields_down_summary),
            onClick = {
                shields.setUp(host, !isUp)
                tab.reload()
                dismiss()
            },
            trailingContent = { Switch(checked = isUp, onCheckedChange = null) },
        )
        if (isUp) {
            HorizontalDivider()
            SonaOptionRow(
                label = pluralStringResource(R.plurals.browser_shields_blocked, tab.blockedCount, tab.blockedCount),
                icon = SonaIcons.Block,
                onClick = {},
                enabled = false,
            )
            SonaOptionRow(
                label = pluralStringResource(R.plurals.browser_shields_upgraded, tab.upgradedCount, tab.upgradedCount),
                icon = SonaIcons.Lock,
                onClick = {},
                enabled = false,
            )
        }
        HorizontalDivider()
        MenuRow(SonaIcons.Settings, stringResource(R.string.browser_shields_settings), onOpenSettings)
    }
}

/** [tab]'s site's icon - the globe while it has none. */
@Composable
private fun PageIcon(tab: BrowserTab?, size: Dp = 24.dp) {
    val icon = tab?.icon
    val bitmap = remember(icon) { icon?.asImageBitmap() }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Icon(SonaIcons.Language, contentDescription = null, modifier = Modifier.size(size), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onNewTab: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onCloseTab: () -> Unit,
    onFindInPage: () -> Unit,
    onZoom: () -> Unit,
    onTranslate: () -> Unit,
    onReadingMode: () -> Unit,
    onDesktopSite: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val url = tab?.url
    SonaBottomSheet(title = tab?.title ?: stringResource(R.string.browser_menu), onDismissRequest = onDismiss) {
        MenuRow(SonaIcons.Add, stringResource(R.string.browser_new_tab), onNewTab)
        // The page's own bookmark is the bottom bar's; here, all of them.
        MenuRow(SonaIcons.Bookmark, stringResource(R.string.browser_bookmarks), onOpenBookmarks)
        if (tab != null) {
            HorizontalDivider()
            MenuRow(SonaIcons.FindInPage, stringResource(R.string.browser_find_in_page), onFindInPage)
            MenuRow(SonaIcons.ZoomIn, stringResource(R.string.browser_zoom), onZoom)
            if (url != null) {
                MenuRow(SonaIcons.Translate, stringResource(R.string.browser_translate), onTranslate)
                MenuRow(SonaIcons.Article, stringResource(R.string.browser_reading_mode), onReadingMode)
            }
            SonaOptionRow(
                label = stringResource(R.string.browser_desktop_site),
                icon = SonaIcons.Computer,
                onClick = {
                    onDesktopSite(!tab.isDesktopSite)
                    dismiss()
                },
                trailingContent = { Switch(checked = tab.isDesktopSite, onCheckedChange = null) },
            )
            HorizontalDivider()
            MenuRow(SonaIcons.Close, stringResource(R.string.browser_close_tab), onCloseTab)
        }
    }
}

/**
 * What a link held in a page can do: open in a tab, in front or behind; be shared, downloaded, or bookmarked. The link itself,
 * at the top, is copied by a long press - with no ripple, as text held is - and the app's toast saying so.
 */
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
    SonaBottomSheet(
        onDismissRequest = onDismiss,
        header = {
            SheetTitle(link, modifier = Modifier.pointerInput(link) {
                detectTapGestures(onLongPress = { context.copyToClipboard(link, copiedLabel) })
            })
        },
    ) {
        MenuRow(SonaIcons.OpenInNew, stringResource(R.string.browser_open_in_new_tab), onOpenInNewTab)
        MenuRow(SonaIcons.Tab, stringResource(R.string.browser_open_in_background), onOpenInBackground)
        MenuRow(SonaIcons.Share, stringResource(R.string.browser_share), onShare)
        MenuRow(SonaIcons.Download, stringResource(R.string.browser_download), onDownload)
        BookmarkRow(link, isBookmarked, { onAddBookmark() }, { onRemoveBookmark() })
    }
}

/** What takes the address bar's place for a while, for the page in front: finding in it, or zooming it. */
private enum class PageTool { FIND, ZOOM }

/** A field of the browser's bar: on its own container, with no line under it. */
@Composable
private fun browserFieldColors() = TextFieldDefaults.colors(
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
)

/** What is looked for in the page, every match marked as it is typed: which is shown of how many, the one before or after. */
@Composable
private fun FindBar(tab: BrowserTab, onClose: () -> Unit) {
    val query = rememberTextFieldState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(query) { snapshotFlow { query.text.toString() }.collect(tab::find) }
    DisposableEffect(tab) { onDispose(tab::clearFind) }
    BackHandler(onBack = onClose)
    val hasMatches = (tab.foundMatches?.second ?: 0) > 0
    // Read here rather than inside the group: it builds its buttons outside composition.
    val previousLabel = stringResource(R.string.browser_find_previous)
    val nextLabel = stringResource(R.string.browser_find_next)
    val closeLabel = stringResource(R.string.browser_close_tool)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextField(
            state = query,
            placeholder = { Text(stringResource(R.string.browser_find_in_page)) },
            suffix = { tab.foundMatches?.let { (shown, of) -> Text(stringResource(R.string.browser_find_count, shown, of)) } },
            lineLimits = TextFieldLineLimits.SingleLine,
            shape = CircleShape,
            colors = browserFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            onKeyboardAction = { tab.findNext(forward = true) },
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
        )
        SonaIconButtonGroup {
            iconButton(SonaIcons.KeyboardArrowUp, previousLabel, { tab.findNext(forward = false) }, enabled = hasMatches)
            iconButton(SonaIcons.KeyboardArrowDown, nextLabel, { tab.findNext(forward = true) }, enabled = hasMatches)
            iconButton(SonaIcons.Close, closeLabel, onClose)
        }
    }
}

/** How large the page's text is drawn: a step smaller or larger, as a percent of its own. */
@Composable
private fun ZoomBar(tab: BrowserTab, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    // Read here rather than inside the groups: they build their buttons outside composition.
    val zoomOutLabel = stringResource(R.string.browser_zoom_out)
    val zoomInLabel = stringResource(R.string.browser_zoom_in)
    val closeLabel = stringResource(R.string.browser_close_tool)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = TextFieldDefaults.MinHeight)) {
        Icon(SonaIcons.ZoomIn, contentDescription = null, modifier = Modifier.padding(horizontal = 12.dp))
        Text(stringResource(R.string.browser_zoom), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        SonaIconButtonGroup {
            iconButton(SonaIcons.Remove, zoomOutLabel, { tab.setTextZoom(tab.textZoom - TextZoomStep) }, enabled = tab.textZoom > MinTextZoom)
        }
        Text(
            stringResource(R.string.browser_zoom_percent, tab.textZoom),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 56.dp),
        )
        SonaIconButtonGroup {
            iconButton(SonaIcons.Add, zoomInLabel, { tab.setTextZoom(tab.textZoom + TextZoomStep) }, enabled = tab.textZoom < MaxTextZoom)
            iconButton(SonaIcons.Close, closeLabel, onClose)
        }
    }
}

/** [this] as CSS takes a colour: `#RRGGBB`. */
private fun Color.cssHex(): String = String.format("#%06X", 0xFFFFFF and toArgb())

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
    val title = rememberTextFieldState(edited.initial.title)
    val url = rememberTextFieldState(edited.initial.url)
    val canSave = title.text.isNotBlank() && url.text.isNotBlank()
    fun save() = onSave(BrowserBookmark(url.text.trim().toString(), title.text.trim().toString()))
    val cancelLabel = stringResource(R.string.download_cancel)
    val saveLabel = stringResource(R.string.browser_save)
    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(if (edited.replacing != null) R.string.browser_edit_bookmark else R.string.browser_add_bookmark),
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = saveLabel, enabled = canSave, onClick = ::save)
        },
    ) {
        OutlinedTextField(
            state = title,
            label = { Text(stringResource(R.string.browser_bookmark_title)) },
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            state = url,
            label = { Text(stringResource(R.string.browser_bookmark_url)) },
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            onKeyboardAction = { if (canSave) save() },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}
