package com.lhacenmed.sona.feature.vault

import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** The biometrics the Private Folder opens with: a fingerprint or a face, however strongly the device reads it. */
private const val VaultAuthenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK

/**
 * Asks the system for the user's fingerprint or face - its own prompt, titled [title], [cancelLabel] the way out of it -
 * and tells [onSucceeded] once it is given. Null where the device has none set up to give, so a caller offers biometrics
 * only where they work.
 */
@Composable
internal fun rememberBiometricCheck(title: String, cancelLabel: String, onSucceeded: () -> Unit): (() -> Unit)? {
    val activity = LocalActivity.current as? FragmentActivity ?: return null
    val canCheck = remember(activity) {
        BiometricManager.from(activity).canAuthenticate(VaultAuthenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }
    if (!canCheck) return null
    val latestOnSucceeded by rememberUpdatedState(onSucceeded)
    return remember(activity, title, cancelLabel) {
        {
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = latestOnSucceeded()
            }
            BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setNegativeButtonText(cancelLabel)
                    .setAllowedAuthenticators(VaultAuthenticators)
                    .build(),
            )
        }
    }
}
