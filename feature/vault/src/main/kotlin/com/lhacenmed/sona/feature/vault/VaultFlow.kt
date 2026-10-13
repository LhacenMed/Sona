package com.lhacenmed.sona.feature.vault

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.core.vault.VaultState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** What a PIN is being chosen for - which names its steps, and decides where confirming it leads. */
internal enum class PinPurpose(val title: String) {
    CREATE("Create Private Folder"),
    RESET("Forgot PIN"),
    CHANGE("Change PIN"),
}

/**
 * The Private Folder's steps under way - each a screen of its own, opened as every screen in the app is -
 * and what one hands the next: the PIN chosen, the security answer checked. Those are kept in memory only,
 * never in an Intent or the saved state, so a step restored without them closes the flow instead.
 */
@Singleton
internal class VaultFlow @Inject constructor() {
    var chosenPin: String? = null
    var verifiedAnswer: String? = null

    private val openSteps = mutableListOf<() -> Unit>()

    fun onStepOpened(close: () -> Unit) {
        openSteps += close
    }

    /** [isLeaving]: gone for good, not recreated - the last step gone so takes what it was handed with it. */
    fun onStepClosed(close: () -> Unit, isLeaving: Boolean) {
        openSteps -= close
        if (isLeaving && openSteps.isEmpty()) {
            chosenPin = null
            verifiedAnswer = null
        }
    }

    /** Closes every open step at once - the flow is done, and whatever lies under it shows. */
    fun finish() {
        openSteps.toList().forEach { it() }
    }
}

/** The screen the Private Folder opens on: set up, its PIN, or itself - null while it is still being read. */
internal fun VaultState.entryScreen(): Screen? = when (this) {
    VaultState.Loading -> null
    VaultState.NotCreated -> VaultChoosePinScreen(PinPurpose.CREATE)
    is VaultState.Created -> if (isUnlocked) PrivateFolderScreen else VaultUnlockScreen
}

/** Opens the Private Folder on the screen it stands at - see [entryScreen]. Settings' way in. */
@Composable
fun rememberPrivateFolderOpener(): () -> Unit {
    val navigator = LocalNavigator.current
    val viewModel: VaultAccessViewModel = hiltViewModel()
    return { viewModel.state.value.entryScreen()?.let(navigator::go) }
}

/**
 * Every step's screen: its own bar titled [title] over [content], and open in [VaultFlow] until its
 * activity is gone. [needsOpenVault]: a step taken from inside the folder - locked meanwhile, the flow closes.
 */
@Composable
internal fun VaultStepScaffold(
    title: String,
    needsOpenVault: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val navigator = LocalNavigator.current
    val activity = LocalActivity.current
    val viewModel: VaultAccessViewModel = hiltViewModel()
    DisposableEffect(navigator) {
        val close = navigator::close
        viewModel.flow.onStepOpened(close)
        onDispose { viewModel.flow.onStepClosed(close, isLeaving = activity?.isChangingConfigurations != true) }
    }
    if (needsOpenVault) {
        LaunchedEffect(Unit) {
            viewModel.state.first { it is VaultState.Created && !it.isUnlocked }
            viewModel.flow.finish()
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        SonaTopAppBar(title = title, onNavigateBack = navigator::back)
        content()
    }
}
