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
}
