package com.lhacenmed.sona.feature.backup.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Does what a backup's notification was pressed for - pause, resume, stop or try again - off the main thread. */
class BackupActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = BackupAction.entries.firstOrNull { it.intentAction == intent.action } ?: return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    BackupAction.PAUSE -> BackupWork.pauseBackup(appContext)
                    BackupAction.RESUME -> BackupWork.resumeBackup(appContext)
                    BackupAction.STOP -> BackupWork.stopBackup(appContext)
                    BackupAction.RETRY -> BackupWork.backUp(appContext)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
