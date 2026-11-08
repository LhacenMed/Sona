package com.lhacenmed.sona.feature.feedback

import android.content.Context
import com.lhacenmed.sona.core.common.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The last crash, kept on the device for the user to report - never sent by itself. As Sona closes on one its trace is
 * written down; the next time it opens, [pendingCrash] holds it, for [CrashReportPrompt] to offer reporting it. Taken
 * or dismissed, it is gone, so a crash is asked about once.
 */
@Singleton
class CrashReports @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val file = File(context.noBackupFilesDir, "last_crash.txt")

    private val _pendingCrash = MutableStateFlow<String?>(null)

    /** The trace of the crash Sona last closed on, until it is reported or dismissed. */
    val pendingCrash: StateFlow<String?> = _pendingCrash.asStateFlow()

    /**
     * Writes down whatever crash ends this process, before Android's own handling of it goes on as ever - and reads the
     * one the last run ended on. Called once, as the process starts.
     */
    fun install() {
        val androidHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Written there and then: the process ends right after.
            runCatching { file.writeText("Thread: ${thread.name}\n${throwable.stackTraceToString()}") }
            androidHandler?.uncaughtException(thread, throwable)
        }
        scope.launch(Dispatchers.IO) { _pendingCrash.value = file.takeIf { it.exists() }?.readText() }
    }

    /** The trace, for a report about it - no longer pending. */
    fun take(): String? = _pendingCrash.value.also { dismiss() }

    fun dismiss() {
        _pendingCrash.value = null
        scope.launch(Dispatchers.IO) { file.delete() }
    }
}
