package com.lhacenmed.sona.feature.feedback

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Hands a report's log file to the email app it is sent with - Feedback's own, apart from the updater's. */
class FeedbackFileProvider : FileProvider(R.xml.feedback_file_paths)

/** [log], written down as a file an email app can attach - in place of the last report's. Blocks. */
internal fun Context.feedbackLogFile(log: String): Uri {
    val file = File(cacheDir, "feedback/sona-log.txt").apply {
        parentFile?.mkdirs()
        writeText(log)
    }
    return FileProvider.getUriForFile(this, "$packageName.feedback", file)
}
