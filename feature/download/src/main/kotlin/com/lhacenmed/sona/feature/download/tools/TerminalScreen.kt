package com.lhacenmed.sona.feature.download.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.DownloadSettings
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.downloader.YtDlp
import com.lhacenmed.sona.core.downloader.splitArguments
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.download.R
import com.lhacenmed.sona.feature.download.downloadFolderOf
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits

/** The most lines the terminal keeps of what yt-dlp wrote. */
private const val KeptLines = 2_000

/**
 * yt-dlp run by hand - YTDLnis's terminal: its arguments typed as on a command line, everything it writes shown as it writes
 * it, and stopped at will. Files it saves go where its arguments say, the app's own downloads folder unless they say otherwise.
 */
data object TerminalScreen : Screen {
    override val titleRes: Int get() = R.string.download_terminal_title

    @Composable
    override fun Content() {
        val viewModel: TerminalViewModel = hiltViewModel()
        val lines by viewModel.lines.collectAsStateWithLifecycle()
        val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
        val arguments = rememberTextFieldState("--version")

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    state = arguments,
                    label = { Text(stringResource(R.string.download_terminal_arguments)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    onKeyboardAction = { if (!isRunning) viewModel.run(arguments.text.toString()) },
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { if (isRunning) viewModel.stop() else viewModel.run(arguments.text.toString()) }, shapes = buttonPressShapes()) {
                    Text(stringResource(if (isRunning) R.string.download_terminal_stop else R.string.download_terminal_run))
                }
            }
            DownloaderOutput(lines, Modifier.fillMaxSize())
        }
    }
}

@HiltViewModel
internal class TerminalViewModel @Inject constructor(
    private val ytDlp: YtDlp,
    private val settings: DownloadSettings,
) : ViewModel() {
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var job: Job? = null

    fun run(arguments: String) {
        job?.cancel()
        _lines.value = listOf("$ yt-dlp $arguments")
        job = viewModelScope.launch {
            _isRunning.value = true
            try {
                // In the videos' folder: what yt-dlp downloads untold is a video.
                ytDlp.runTyped(splitArguments(arguments), downloadFolderOf(settings.preferences.value, DownloadKind.VIDEO, runsCommand = true), ::append)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                append(e.message ?: e.toString())
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private fun append(line: String) {
        _lines.update { (it + line).takeLast(KeptLines) }
    }
}
