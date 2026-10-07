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

/** Creating the vault, and every way into it - its PIN, its security answer, and a new PIN after either. */
@HiltViewModel
internal class VaultAccessViewModel @Inject constructor(private val vault: VaultRepository) : ViewModel() {

    val state: StateFlow<VaultState> = vault.state

    /** [opens]: whether the vault is unlocked once created - inside the Private Folder, but not when created to move a file in. */
    fun create(pin: String, question: String, answer: String, opens: Boolean) {
        viewModelScope.launch {
            vault.create(pin, question, answer)
            if (opens) vault.unlock(pin)
        }
    }

    fun unlock(pin: String, onResult: (VaultAccessResult) -> Unit) {
        viewModelScope.launch { onResult(vault.unlock(pin)) }
    }

    fun verifySecurityAnswer(answer: String, onResult: (VaultAccessResult) -> Unit) {
        viewModelScope.launch { onResult(vault.verifySecurityAnswer(answer)) }
    }

    /** The folder opens as it succeeds, so there is nothing more to hear back. */
    fun resetPin(answer: String, newPin: String) {
        viewModelScope.launch { vault.resetPin(answer, newPin) }
    }

    fun changePin(newPin: String, onChanged: () -> Unit) {
        viewModelScope.launch {
            vault.changePin(newPin)
            onChanged()
        }
    }
}
