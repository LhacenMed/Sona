package com.lhacenmed.sona.feature.library.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.fastscroll.FastScroller
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.R

/**
 * The library's tabs - which are shown, and in what order, dragged by their handles. The first shown is the one the
 * library opens on; the strip itself is reordered the same way, a tab held and dragged along it.
 */
data object LibraryTabsScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val listState = rememberLazyListState()
        val editor = rememberLibraryTabEditor(listState)

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(title = stringResource(R.string.library_tabs_title), onNavigateBack = navigator::back)
            FastScroller(listState, Modifier.fillMaxSize()) { overscrollEffect ->
                LazyColumn(
                    state = listState,
                    overscrollEffect = overscrollEffect,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomContentPadding.current),
                ) {
                    libraryTabRows(editor)
                }
            }
        }
    }
}
