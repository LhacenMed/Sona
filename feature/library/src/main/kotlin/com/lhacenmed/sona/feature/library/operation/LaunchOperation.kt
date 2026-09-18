package com.lhacenmed.sona.feature.library.operation

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Runs a change [onFinished] is waiting to hear the outcome of - what backs every
 * [ConfirmedOperationDialog]. A screen closing mid-way cancels the wait, which is not the change
 * failing, so that is never reported as a failure.
 */
internal fun CoroutineScope.launchOperation(
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
