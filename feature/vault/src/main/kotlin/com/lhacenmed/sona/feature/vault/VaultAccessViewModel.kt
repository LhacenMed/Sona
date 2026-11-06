package com.lhacenmed.sona.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.vault.VaultAccessResult
import com.lhacenmed.sona.core.vault.VaultRepository
import com.lhacenmed.sona.core.vault.VaultState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Creating the vault, every way into it - its PIN, its security answer, a new PIN after either, its biometrics - and how it is found. */
@HiltViewModel
internal class VaultAccessViewModel @Inject constructor(
    private val vault: VaultRepository,
    val flow: VaultFlow,
) : ViewModel() {

    val state: StateFlow<VaultState> = vault.state

    /** Created open - see [VaultRepository.create]. */
    fun create(pin: String, question: String, answer: String, onCreated: () -> Unit) {
        viewModelScope.launch {
            vault.create(pin, question, answer)
            onCreated()
        }
    }

    fun unlock(pin: String, onResult: (VaultAccessResult) -> Unit) {
        viewModelScope.launch { onResult(vault.unlock(pin)) }
    }

    fun verifySecurityAnswer(answer: String, onResult: (VaultAccessResult) -> Unit) {
        viewModelScope.launch { onResult(vault.verifySecurityAnswer(answer)) }
    }

    /** The folder opens as it is [VaultAccessResult.Granted]. */
    fun resetPin(answer: String, newPin: String, onResult: (VaultAccessResult) -> Unit) {
        viewModelScope.launch { onResult(vault.resetPin(answer, newPin)) }
    }

    fun changePin(newPin: String, onChanged: () -> Unit) {
        viewModelScope.launch {
            vault.changePin(newPin)
            onChanged()
        }
    }

    /** Opens the folder on the fingerprint or face the system has just checked - see [VaultRepository.unlockWithBiometrics]. */
    fun unlockWithBiometrics(onUnlocked: () -> Unit) {
        viewModelScope.launch {
            vault.unlockWithBiometrics()
            onUnlocked()
        }
    }

    /**
     * Keeps [code] as the secret code - none, while null - hiding the folder too where [hidesFolder] asks it to, and
     * tells [onSaved] once all of it is kept.
     */
    fun setSecretCode(code: String?, hidesFolder: Boolean, onSaved: () -> Unit) {
        viewModelScope.launch {
            vault.setSecretCode(code)
            if (hidesFolder) vault.setHidden(true)
            onSaved()
        }
    }

    fun setHidden(hidden: Boolean) {
        viewModelScope.launch { vault.setHidden(hidden) }
    }

    fun setUnlocksWithBiometrics(enabled: Boolean) {
        viewModelScope.launch { vault.setUnlocksWithBiometrics(enabled) }
    }
}
