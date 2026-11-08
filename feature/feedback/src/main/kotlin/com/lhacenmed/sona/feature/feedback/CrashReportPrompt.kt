package com.lhacenmed.sona.feature.feedback

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.navigation.LocalNavigator

/**
 * After Sona has closed on a crash, the next time it opens: what happened, and the way to report it - a bug report
 * with the crash's trace, for the user to read before sending - or to let it be. Asked once, whichever is chosen.
 */
@Composable
fun CrashReportPrompt(crashReports: CrashReports) {
    val pendingCrash by crashReports.pendingCrash.collectAsStateWithLifecycle()
    if (pendingCrash == null) return
    val navigator = LocalNavigator.current
    val notNowLabel = stringResource(R.string.feedback_crash_not_now)
    val reportLabel = stringResource(R.string.feedback_crash_report)
    SonaDialog(
        onDismissRequest = crashReports::dismiss,
        title = stringResource(R.string.feedback_crash_title),
        buttons = {
            actionButton(label = notNowLabel, onClick = crashReports::dismiss)
            actionButton(
                label = reportLabel,
                // Taken as it is chosen, so the prompt is gone before the report's screen opens under it.
                onClick = { navigator.go(FeedbackScreen(crash = crashReports.take())) },
            )
        },
    ) {
        Text(stringResource(R.string.feedback_crash_message))
    }
}
