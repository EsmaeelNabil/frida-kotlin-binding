package dev.supersam.frida

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FridaScript(val handle: Long) : IFridaScript {

    /**
     * Raw JSON strings from the script's send() / console calls.
     * Backed by the Frida "message" GObject signal.
     * Use [typedMessages] for parsed [ScriptMessage] values.
     */
    override val messages: Flow<String> = callbackFlow {
        val callback = FridaNative.MessageCallback { message ->
            trySend(message)
        }
        val cbHandle = FridaNative.scriptConnectMessage(handle, callback)
        awaitClose {
            FridaNative.scriptDisconnectMessage(handle, cbHandle)
        }
    }

    /**
     * Parsed version of [messages]. Each raw JSON string is decoded into
     * a [ScriptMessage.Send], [ScriptMessage.Error], [ScriptMessage.Log],
     * or [ScriptMessage.Raw] for unknown types.
     */
    override val typedMessages: Flow<ScriptMessage> = messages.map { FridaMessageParser.parse(it) }

    /**
     * Emits [Unit] once when the script is destroyed (e.g. due to an uncaught exception
     * that crashed the runtime, or when the session is detached), then closes.
     * Backed by the Frida "destroyed" GObject signal.
     */
    override val destroyed: Flow<Unit> = callbackFlow {
        val callback = FridaNative.DestroyedCallback {
            trySend(Unit)
            close()
        }
        val cbHandle = FridaNative.scriptConnectDestroyed(handle, callback)
        awaitClose {
            FridaNative.scriptDisconnectDestroyed(handle, cbHandle)
        }
    }

    override suspend fun load() = withContext(Dispatchers.IO) {
        FridaNative.scriptLoad(handle)
    }

    override suspend fun unload() = withContext(Dispatchers.IO) {
        FridaNative.scriptUnload(handle)
        FridaNative.unref(handle)
    }

    /** Send a JSON message to the script (received via script.recv() / rpc.exports). */
    override fun post(message: String) {
        FridaNative.scriptPost(handle, message)
    }
}
