package dev.supersam.frida

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FridaScript(val handle: Long) {

    /**
     * Raw JSON strings from the script's send() / console calls.
     * Backed by the Frida "message" GObject signal.
     * Use [typedMessages] for parsed [ScriptMessage] values.
     */
    val messages: Flow<String> = callbackFlow {
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
    val typedMessages: Flow<ScriptMessage> = messages.map { FridaMessageParser.parse(it) }

    suspend fun load() = withContext(Dispatchers.IO) {
        FridaNative.scriptLoad(handle)
    }

    suspend fun unload() = withContext(Dispatchers.IO) {
        FridaNative.scriptUnload(handle)
        FridaNative.unref(handle)
    }

    /** Send a JSON message to the script (received via script.recv() / rpc.exports). */
    fun post(message: String) {
        FridaNative.scriptPost(handle, message)
    }
}
