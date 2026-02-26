package dev.supersam.frida

import frida_core.*
import kotlinx.cinterop.*
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalForeignApi::class)
class FridaScript(val handle: CPointer<FridaScript>) : IFridaScript {

    override val messages: Flow<String> = callbackFlow {
        val ref = StableRef.create(this)
        val handlerId = g_signal_connect_data(
            handle,
            "message",
            staticCFunction(::onMessage),
            ref.asCPointer(),
            staticCFunction(::onDestroyRef),
            0u
        )
        awaitClose {
            g_signal_handler_disconnect(handle, handlerId)
        }
    }

    override val typedMessages: Flow<ScriptMessage> = messages.map { FridaMessageParser.parse(it) }

    override val destroyed: Flow<Unit> = callbackFlow {
        val ref = StableRef.create(this)
        val handlerId = g_signal_connect_data(
            handle,
            "destroyed",
            staticCFunction(::onDestroyed),
            ref.asCPointer(),
            staticCFunction(::onDestroyRef),
            0u
        )
        awaitClose {
            g_signal_handler_disconnect(handle, handlerId)
        }
    }

    override suspend fun load() = memScoped {
        checkError { err ->
            frida_script_load_sync(handle, null, err.ptr)
        }
    }

    override suspend fun unload() = memScoped {
        checkError { err ->
            frida_script_unload_sync(handle, null, err.ptr)
        }
        g_object_unref(handle)
    }

    override fun post(message: String) {
        frida_script_post(handle, message, null)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun onMessage(script: CPointer<FridaScript>?, message: CPointer<ByteVar>?, data: CPointer<GBytes>?, userData: COpaquePointer?) {
    if (userData == null) return
    val scope = userData.asStableRef<ProducerScope<String>>().get()
    val msg = message?.toKString() ?: ""
    scope.trySend(msg)
}

@OptIn(ExperimentalForeignApi::class)
private fun onDestroyed(script: CPointer<FridaScript>?, userData: COpaquePointer?) {
    if (userData == null) return
    val scope = userData.asStableRef<ProducerScope<Unit>>().get()
    scope.trySend(Unit)
    scope.close()
}
