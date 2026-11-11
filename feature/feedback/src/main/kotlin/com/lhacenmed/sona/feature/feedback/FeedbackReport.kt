package com.lhacenmed.sona.feature.feedback

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.lhacenmed.sona.feature.update.github.GitHub
import com.lhacenmed.sona.feature.update.installedBuild

/** Where feedback is sent - the project's own address, as its docs give it. */
private const val CONTACT_EMAIL = "sona.app.contact@gmail.com"

/** The repository's issue form the app fills in, `.github/ISSUE_TEMPLATE/app_feedback.yml`: one answer, then the details. */
private const val ISSUE_FORM = "app_feedback.yml"

/**
 * The longest a GitHub link may be for its issue form to open: GitHub refuses one much longer with "URI too long".
 * Room for the message and the details, with the log cut down to what is left.
 */
private const val MAX_ISSUE_URL_LENGTH = 8_000

/** How much of a message's first line names it - what a list of reports shows of each. */
private const val TITLE_LENGTH = 60

/**
 * What a report carries of the phone it was written on, for whoever reads it to find what went wrong: the build
 * installed, the phone and its Android, and the log - a crash's trace first, then what the app logged lately.
 */
internal class Diagnostics(val version: String, val device: String, val log: String) {
    /** The build and the phone, as the screen names them before anything is sent. */
    val summary: String get() = "Sona $version · $device"

    companion object {
        fun of(context: Context, log: String): Diagnostics {
            val build = context.installedBuild()
            return Diagnostics(
                version = listOfNotNull(build.versionName, build.variant?.id).joinToString(" "),
                device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                log = log,
            )
        }
    }
}

/**
 * Feedback as written: in the user's own words, whatever it is about - something broken, too small, confusing, or an
 * idea - with the screenshots and recordings they picked, and the phone's [diagnostics] unless they left them out.
 */
internal class FeedbackReport(
    val message: String,
    val attachments: List<Uri>,
    val diagnostics: Diagnostics?,
) {
    /** Its first line, cut short: what names it in a list. */
    val title: String
        get() {
            val firstLine = message.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
            return if (firstLine.length > TITLE_LENGTH) firstLine.take(TITLE_LENGTH).trimEnd() + "…" else firstLine
        }

    /**
     * An email to the project with everything in it - the message, the pictures and recordings, and the log as a file,
     * [logFile] - opened straight in the phone's email app: the one it opens email in by default, or its only one. With
     * several and none the default, the user picks among them alone; with none, any app that takes it will do. Needs no
     * account of any kind.
     *
     * Each email app is named rather than asked for as "an email app", which some makers' builds of Android answer with
     * the share sheet - or with no app at all - once there are files to attach.
     */
    fun emailIntent(context: Context, logFile: Uri?, chooserTitle: String): Intent {
        val email = message(logFile)
        val packageManager = context.packageManager
        val writesEmail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        val emailApps = packageManager.queryIntentActivities(writesEmail, 0).map { it.activityInfo.packageName }.distinct()
        // With no default, Android answers with its own app picker - which is no email app.
        val defaultApp = packageManager.resolveActivity(writesEmail, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeIf { it in emailApps }
        (defaultApp ?: emailApps.singleOrNull())?.let { return email.setPackage(it) }
        if (emailApps.isEmpty()) return Intent.createChooser(email, chooserTitle)
        return Intent.createChooser(Intent(email).setPackage(emailApps.first()), chooserTitle)
            .putExtra(Intent.EXTRA_INITIAL_INTENTS, emailApps.drop(1).map { Intent(email).setPackage(it) }.toTypedArray())
    }

    /** The email in the share sheet, for an email app that turns down the files it was handed. */
    fun shareIntent(logFile: Uri?, chooserTitle: String): Intent = Intent.createChooser(message(logFile), chooserTitle)

    /** The email itself - written to the project, with the files attached and lent to the app that sends it. */
    private fun message(logFile: Uri?): Intent {
        val files = attachments + listOfNotNull(logFile)
        val body = buildString {
            append(message)
            diagnostics?.let { append("\n\n---\n").append(it.version).append("\n").append(it.device) }
        }
        if (files.isEmpty()) return Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).withMessage(body)
        return Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("*/*")
            .withMessage(body)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files))
            .apply {
                // The email app may read the files for as long as it has them: the pictures were only lent to Sona.
                clipData = ClipData.newRawUri(null, files.first()).apply { files.drop(1).forEach { addItem(ClipData.Item(it)) } }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
    }

    private fun Intent.withMessage(body: String): Intent = this
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(CONTACT_EMAIL))
        .putExtra(Intent.EXTRA_SUBJECT, "Sona feedback: $title")
        .putExtra(Intent.EXTRA_TEXT, body)

    /**
     * The same report as a public issue on GitHub, the app's issue form opened already filled in, to be posted from the
     * user's account. A link carries no files: the pictures are added on the page. The log is cut from its start until
     * the link fits.
     */
    fun issueUrl(): Uri {
        fun build(log: String?): Uri = Uri.parse(GitHub.NEW_ISSUE_URL).buildUpon()
            .appendQueryParameter("template", ISSUE_FORM)
            .appendQueryParameter("title", title)
            .appendQueryParameter("description", message)
            .apply {
                diagnostics?.let {
                    appendQueryParameter("version", it.version)
                    appendQueryParameter("device", it.device)
                }
                log?.let { appendQueryParameter("logs", it) }
            }
            .build()

        val log = diagnostics?.log?.takeIf { it.isNotBlank() } ?: return build(log = null)
        val room = MAX_ISSUE_URL_LENGTH - build(log = "").toString().length
        // Encoded, a character of the log takes up to three of the link's: kept within that, it always fits.
        return build(log.takeLast((room / 3).coerceAtLeast(0)))
    }
}
