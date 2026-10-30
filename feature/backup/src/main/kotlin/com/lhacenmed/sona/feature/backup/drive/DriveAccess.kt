package com.lhacenmed.sona.feature.backup.drive

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.lhacenmed.sona.feature.backup.BackupSettings
import dagger.Lazy
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

private const val GOOGLE_ACCOUNT_TYPE = "com.google"

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
 * restore. Google keeps the grant and its tokens, and the account it was given for - the one last picked in its
 * account picker, which a request naming no account is answered for. Sona keeps only that access was given, and
 * the account's email address as Drive gives it, to show it.
 */
@Singleton
class DriveAccess @Inject internal constructor(
    @ApplicationContext context: Context,
    private val backupSettings: BackupSettings,
    // Lazily: the API asks this for its tokens, so neither can be made first.
    private val api: Lazy<DriveApi>,
) {
    private val client = Identity.getAuthorizationClient(context)

    /** What every backup and restore asks for, naming no account - see [token]. */
    private val request = AuthorizationRequest.builder().setRequestedScopes(listOf(DRIVE_FILE_SCOPE)).build()

    val isConnected: StateFlow<Boolean> = backupSettings.isDriveConnected

    /** The connected account's email address, or null while none is connected. */
    val account: StateFlow<String?> = backupSettings.driveAccount

    /**
     * Connects - always through Google's account picker, so another account than the last can be chosen - once
     * the user picks one and agrees in [DriveConnection.NeedsConsent], or at once where Google needs nothing more.
     */
    suspend fun connect(): DriveConnection {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(DRIVE_FILE_SCOPE))
            .setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
            .build()
        val result = client.authorize(request).await()
        val consent = result.pendingIntent
        if (result.hasResolution() && consent != null) return DriveConnection.NeedsConsent(consent)
        connected(checkNotNull(result.accessToken))
        return DriveConnection.Connected
    }

    /** Finishes connecting with what the consent screen returned. Whether access was given. */
    suspend fun finishConnecting(data: Intent?): Boolean {
        val token = try {
            client.getAuthorizationResultFromIntent(data).accessToken
        } catch (_: ApiException) {
            null
        } ?: return false
        connected(token)
        return true
    }

    /** Stops backing up to Drive, and takes back the access it was given. What is there stays. */
    suspend fun disconnect() {
        backupSettings.driveAccount.value?.let { email ->
            val request = RevokeAccessRequest.builder().setAccount(Account(email, GOOGLE_ACCOUNT_TYPE)).setScopes(listOf(DRIVE_FILE_SCOPE)).build()
            // Offline, the access is let go of here all the same; the user can still remove it from their account.
            runCatching { client.revokeAccess(request).await() }
        }
        backupSettings.setDriveDisconnected()
    }

    /**
     * A current token for the connected account, without asking the user anything - Google renews it as needed.
     *
     * No account is named: Google answers for the one picked when connecting, which is what it is built to do. Naming
     * it would ask Google to match the email Drive gives against the device's accounts, which is not always the same
     * text. Only Google wanting the user's consent again means the access is gone; anything else - no connection, a
     * busy service - is a failure like any other, for the run to try again later, still connected.
     */
    internal suspend fun token(): String {
        val result: AuthorizationResult = try {
            client.authorize(request).await()
        } catch (e: ApiException) {
            throw IOException("Google gave no token for Drive", e)
        }
        val token = result.accessToken.takeUnless { result.hasResolution() } ?: lose()
        // Connected before its email address was kept: kept now, to show which account it is.
        if (backupSettings.driveAccountNow() == null) connected(token)
        return token
    }

    /** Forgets the connection, which no longer holds, and says so - for the user to connect again. */
    internal suspend fun lose(): Nothing {
        backupSettings.setDriveDisconnected()
        throw DriveAccessLostException()
    }

    /** Lets go of [token], which Drive refused, so the next [token] is a fresh one. */
    internal suspend fun forget(token: String) {
        client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
    }

    /** Keeps the account [token] was given for - by the email address Drive knows it by - as the one connected. */
    private suspend fun connected(token: String) {
        backupSettings.setDriveConnected(account = api.get().accountEmail(token))
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}
