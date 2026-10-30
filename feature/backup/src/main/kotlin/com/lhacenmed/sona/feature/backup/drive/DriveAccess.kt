package com.lhacenmed.sona.feature.backup.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.lhacenmed.sona.feature.backup.BackupSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/** Only the files Sona creates in the user's Drive - nothing else there. */
private val DRIVE_FILE_SCOPE = Scope("https://www.googleapis.com/auth/drive.file")

/** The account's email address alone - to show which account is connected - never its name or profile. */
private val EMAIL_SCOPE = Scope("email")

/** Access to Drive was taken back - from the user's Google Account - since it was given. */
class DriveAccessLostException : IOException()

/** How connecting to Drive went on its first step. */
sealed interface DriveConnection {
    data object Connected : DriveConnection

    /** Google needs the user to choose an account and agree - in the screen [consent] opens. */
    data class NeedsConsent(val consent: PendingIntent) : DriveConnection
}

/**
 * Sona's access to the user's Google Drive: given once, from the Backup screen, then used by every backup and
 * restore. Google keeps the grant and its tokens; Sona keeps only that it was given - and to which account, by its
 * email address, which stays on the device - and asks Google for a token each time it needs one.
 */
@Singleton
class DriveAccess @Inject constructor(
    @ApplicationContext context: Context,
    private val backupSettings: BackupSettings,
) {
    private val client = Identity.getAuthorizationClient(context)
    private val request = AuthorizationRequest.builder().setRequestedScopes(listOf(DRIVE_FILE_SCOPE, EMAIL_SCOPE)).build()

    val isConnected: StateFlow<Boolean> = backupSettings.isDriveConnected

    /** The connected account's email address, or null while none is connected. */
    val account: StateFlow<String?> = backupSettings.driveAccount

    /** Connects - at once where access was given before, else once the user agrees in [DriveConnection.NeedsConsent]. */
    suspend fun connect(): DriveConnection {
        val result = client.authorize(request).await()
        val consent = result.pendingIntent
        if (result.hasResolution() && consent != null) return DriveConnection.NeedsConsent(consent)
        backupSettings.setDriveConnected(account = result.toGoogleSignInAccount()?.email)
        return DriveConnection.Connected
    }

    /** Finishes connecting with what the consent screen returned. Whether access was given. */
    suspend fun finishConnecting(data: Intent?): Boolean {
        val result = try {
            client.getAuthorizationResultFromIntent(data)
        } catch (_: ApiException) {
            null
        }
        if (result?.accessToken == null) return false
        backupSettings.setDriveConnected(account = result.toGoogleSignInAccount()?.email)
        return true
    }

    /** Stops backing up to Drive. What is there stays; the grant itself is the user's to remove from their account. */
    suspend fun disconnect() = backupSettings.setDriveDisconnected()

    /** A current token, without asking the user anything - Google renews it as needed. */
    internal suspend fun token(): String {
        val result: AuthorizationResult = client.authorize(request).await()
        return result.accessToken.takeUnless { result.hasResolution() } ?: run {
            backupSettings.setDriveDisconnected()
            throw DriveAccessLostException()
        }
    }

    /** Lets go of [token], which Drive refused, so the next [token] is a fresh one. */
    internal suspend fun forget(token: String) {
        client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}
