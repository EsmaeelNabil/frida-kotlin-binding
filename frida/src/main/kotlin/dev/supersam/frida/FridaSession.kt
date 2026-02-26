package dev.supersam.frida

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

class FridaSession(val handle: Long) {

    /**
     * Emits once when the session is detached, then closes.
     * Backed by the Frida "detached" GObject signal.
     */
    val detached: Flow<DetachReason> = callbackFlow {
        val callback = FridaNative.DetachedCallback { reason ->
            trySend(DetachReason.fromInt(reason))
            close()
        }
        val cbHandle = FridaNative.sessionConnectDetached(handle, callback)
        awaitClose {
            FridaNative.sessionDisconnectDetached(handle, cbHandle)
        }
    }

    suspend fun createScript(source: String): FridaScript = withContext(Dispatchers.IO) {
        FridaScript(FridaNative.sessionCreateScript(handle, source))
    }

    suspend fun detach() = withContext(Dispatchers.IO) {
        FridaNative.sessionDetach(handle)
        FridaNative.unref(handle)
    }
}
