package dev.supersam.frida

import frida_core.*
import kotlinx.cinterop.*
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@OptIn(ExperimentalForeignApi::class)
class FridaSession(val handle: CPointer<FridaSession>) : IFridaSession {

    override val detached: Flow<DetachReason> = callbackFlow {
        val ref = StableRef.create(this)
        val handlerId = g_signal_connect_data(
            handle,
            "detached",
            staticCFunction(::onDetached),
            ref.asCPointer(),
            staticCFunction(::onDestroyRef),
            0u
        )
        awaitClose {
            g_signal_handler_disconnect(handle, handlerId)
        }
    }

    override suspend fun createScript(source: String): FridaScript = memScoped {
        val opts = frida_script_options_new()
        val script = checkError { err ->
            frida_session_create_script_sync(handle, source, opts, null, err.ptr)
        }
        g_object_unref(opts)
        FridaScript(script!!)
    }

    override suspend fun snapshotScript(
        embedScript: String,
        warmupScript: String?
    ): ScriptSnapshot = memScoped {
        val opts = frida_snapshot_options_new()
        if (warmupScript != null) {
            frida_snapshot_options_set_warmup_script(opts, warmupScript)
        }

        val bytes = checkError { err ->
            frida_session_snapshot_script_sync(handle, embedScript, opts, null, err.ptr)
        }
        g_object_unref(opts)

        val sizeVar = alloc<ULongVar>()
        val dataPtr = g_bytes_get_data(bytes, sizeVar.ptr)
        val dataSize = sizeVar.value.toInt()

        val result = if (dataSize > 0 && dataPtr != null) dataPtr.readBytes(dataSize) else ByteArray(0)

        g_bytes_unref(bytes)
        ScriptSnapshot(result)
    }

    override suspend fun createScriptFromSnapshot(
        snapshot: ScriptSnapshot,
        source: String
    ): FridaScript = memScoped {
        val opts = frida_script_options_new()
        val data = snapshot.bytes
        val dataSize = data.size

        val nativeData = allocArray<ByteVar>(dataSize)
        for (i in 0 until dataSize) {
            nativeData[i] = data[i]
        }

        val gbytes = g_bytes_new(nativeData, dataSize.toULong())
        frida_script_options_set_snapshot(opts, gbytes)
        g_bytes_unref(gbytes)

        val script = checkError { err ->
            frida_session_create_script_sync(handle, source, opts, null, err.ptr)
        }
        g_object_unref(opts)
        FridaScript(script!!)
    }

    override suspend fun detach() = memScoped {
        checkError { err ->
            frida_session_detach_sync(handle, null, err.ptr)
        }
        g_object_unref(handle)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun onDetached(session: CPointer<FridaSession>?, reason: FridaSessionDetachReason, crash: CPointer<FridaCrash>?, userData: COpaquePointer?) {
    if (userData == null) return
    val scope = userData.asStableRef<ProducerScope<DetachReason>>().get()
    // FridaSessionDetachReason is likely UInt (typedef enum)
    val kReason = DetachReason.fromInt(reason.toInt())
    scope.trySend(kReason)
    scope.close()
}
