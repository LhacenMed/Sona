package com.lhacenmed.sona.feature.feedback

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.common.log.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How many of the app's latest log lines a report carries: the moments before what went wrong. */
private const val REPORTED_LOG_LINES = 300

/** The most screenshots and recordings a report takes - plenty to show what is meant, few enough to send. */
internal const val MAX_ATTACHMENTS = 5

/** Feedback as it is being written - see [FeedbackScreen] - and the two ways it is sent. */
@HiltViewModel
internal class FeedbackViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    var message by mutableStateOf("")

    /** The screenshots and recordings picked, in the order they were. */
    val attachments = mutableStateListOf<Uri>()

    /** Whether the phone's details and the app's log go with it - on, as they are what finds the problem. */
    var includesDiagnostics by mutableStateOf(true)

    /** The phone's details and the log, once read - null until then. */
    var diagnostics by mutableStateOf<Diagnostics?>(null)
        private set

    private var hasStarted = false

    /** Starts the report - about [crash], the trace of the crash Sona last closed on, where it is about one. Once only. */
    fun start(crash: String?) {
        if (hasStarted) return
        hasStarted = true
        if (crash != null) message = context.getString(R.string.feedback_crash_message_prefill)
        viewModelScope.launch {
            val log = withContext(Dispatchers.IO) {
                listOfNotNull(crash, AppLog.recentLines(REPORTED_LOG_LINES).joinToString("\n")).joinToString("\n\n")
            }
            diagnostics = Diagnostics.of(context, log)
        }
    }

    val canSend: Boolean get() = message.isNotBlank()

    /** Adds [picked] after those already there, up to [MAX_ATTACHMENTS] in all. */
    fun attach(picked: List<Uri>) {
        attachments += (picked - attachments.toSet()).take(MAX_ATTACHMENTS - attachments.size)
    }

    /**
     * The email that sends the report, its log written down as a file to go with it - handed to [send] once ready, with the
     * same in the share sheet for an email app that turns it down.
     */
    fun email(send: (email: Intent, share: Intent) -> Unit) {
        val report = report()
        viewModelScope.launch {
            val logFile = report.diagnostics?.log?.takeIf { it.isNotBlank() }?.let { log ->
                withContext(Dispatchers.IO) { context.feedbackLogFile(log) }
            }
            val chooserTitle = context.getString(R.string.feedback_send_with)
            send(report.emailIntent(context, logFile, chooserTitle), report.shareIntent(logFile, chooserTitle))
        }
    }

    fun issueUrl(): Uri = report().issueUrl()

    private fun report() = FeedbackReport(
        message = message.trim(),
        attachments = attachments.toList(),
        diagnostics = diagnostics?.takeIf { includesDiagnostics },
    )
}
