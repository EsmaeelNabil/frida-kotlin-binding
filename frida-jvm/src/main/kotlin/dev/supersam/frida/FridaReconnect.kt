package dev.supersam.frida

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Attaches to the process at [pid] on this device, then calls [block] with the
 * resulting [FridaSession].
 *
 * If the session is lost with [DetachReason.DEVICE_LOST] or
 * [DetachReason.CONNECTION_TERMINATED], the function waits [retryDelayMs]
 * milliseconds and re-attaches — up to [maxRetries] times.
 * When retrying, [onReconnect] is called with the attempt number (starting at 1).
 *
 * [block] runs inside a [coroutineScope] that is cancelled the moment the session
 * detaches. Any child coroutines launched inside [block] (e.g. for message
 * collection) are therefore automatically torn down before the retry.
 *
 * The function returns normally when [block] completes without a device-lost
 * detach, or throws [MaxRetriesExceededException] when all retries are exhausted.
 * Parent-coroutine cancellation propagates immediately.
 *
 * ```kotlin
 * device.attachWithReconnect(pid = targetPid) { session ->
 *     val script = session.createScript(source)
 *     launch { script.messages.collect { println(it) } }
 *     script.load()
 *     awaitCancellation()   // keep running until reconnect kicks in
 * }
 * ```
 */
suspend fun FridaDevice.attachWithReconnect(
    pid: Int,
    maxRetries: Int = Int.MAX_VALUE,
    retryDelayMs: Long = 2_000,
    onReconnect: (attempt: Int) -> Unit = {},
    block: suspend (FridaSession) -> Unit
) {
    var retriesLeft = maxRetries
    var attempt = 0

    while (true) {
        val session = attach(pid)
        var shouldRetry = false

        try {
            coroutineScope {
                val outerScope = this
                launch {
                    val reason = session.detached.first()
                    if (reason == DetachReason.DEVICE_LOST ||
                        reason == DetachReason.CONNECTION_TERMINATED
                    ) {
                        shouldRetry = true
                        outerScope.cancel()
                    }
                    // APPLICATION_REQUESTED / PROCESS_TERMINATED etc. — don't retry
                }
                block(session)
            }
        } catch (e: CancellationException) {
            if (!shouldRetry || !currentCoroutineContext().isActive) throw e
        }

        if (!shouldRetry) return

        if (retriesLeft <= 0) throw MaxRetriesExceededException(maxRetries)
        retriesLeft--
        attempt++
        onReconnect(attempt)
        delay(retryDelayMs)
    }
}

/** Thrown by [attachWithReconnect] when [maxRetries] reconnection attempts are exhausted. */
class MaxRetriesExceededException(val maxRetries: Int) :
    Exception("Session reconnect failed after $maxRetries attempt(s)")
