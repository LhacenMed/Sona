@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.debug.logs

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.common.log.AppLog
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.fastscroll.FastScroller
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.debug.Loaded
import com.lhacenmed.sona.feature.debug.rememberLoaded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How many of the latest log lines are read. */
private const val MAX_LOG_LINES = 2_000

/** What this process has logged, newest first, narrowed by what a line must contain. */
data object LogsScreen : Screen {
    override fun title(context: Context): String = "Logs"

    @Composable
    override fun Content() {
        val scope = rememberCoroutineScope()
        var version by remember { mutableIntStateOf(0) }
        var filter by rememberSaveable { mutableStateOf("") }
        val lines = rememberLoaded(version) {
            withContext(Dispatchers.IO) {
                AppLog.recentLines(MAX_LOG_LINES).asReversed()
            }
        }
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("Contains") },
                singleLine = true,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row {
                TextButton(shapes = buttonPressShapes(), onClick = { version++ }) { Text("Read again") }
                TextButton(
                    shapes = buttonPressShapes(),
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { AppLog.clear() }
                            version++
                        }
                    },
                ) { Text("Clear") }
            }
            Loaded(lines) { all ->
                val sideways = rememberScrollState()
                val shown = remember(all, filter) { all.filter { it.contains(filter, ignoreCase = true) } }
                val listState = rememberLazyListState()
                FastScroller(listState, Modifier.fillMaxSize()) { overscrollEffect ->
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().horizontalScroll(sideways),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomContentPadding.current),
                        overscrollEffect = overscrollEffect,
                    ) {
                        items(shown) { line ->
                            Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}
