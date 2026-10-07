package com.lhacenmed.sona.core.vault

import android.content.Context
import java.io.File

/**
 * Where everything of the Private Folder lives - its files, their list and its PIN: app-internal storage,
 * which no other app can read, under `noBackupFilesDir`, which Android leaves out of every cloud backup and
 * device transfer. So the folder is never half-restored somewhere - its list without its files, or its
 * files without its PIN.
 */
internal val Context.vaultDirectory: File
    get() = File(noBackupFilesDir, "vault").apply { mkdirs() }
