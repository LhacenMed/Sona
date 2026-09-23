package com.lhacenmed.sona.core.common.coroutines

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Runs a change [onFinished] is waiting to hear the outcome of - what backs every confirmation dialog
 * that stays up until its change is done. A screen closing mid-way cancels the wait, which is not the
 * change failing, so that is never reported as a failure.
 */
fun CoroutineScope.launchOperation(
    onFinished: (succeeded: Boolean) -> Unit,
    operation: suspend () -> Unit,
) {
    launch {
        val succeeded = try {
            operation()
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            false
        }
        onFinished(succeeded)
    }
}
