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

    /**
     * Creates a V8 heap snapshot by running [embedScript] inside an isolated V8 context.
     * The resulting [ScriptSnapshot] can be passed to [createScriptFromSnapshot] to start
     * subsequent scripts with pre-initialized state, dramatically reducing startup time.
     *
     * @param embedScript  JavaScript that populates the heap (e.g. `require` calls, class defs).
     * @param warmupScript Optional script run after [embedScript] to warm up JIT compiled code.
     */
    suspend fun snapshotScript(
        embedScript: String,
        warmupScript: String? = null
    ): ScriptSnapshot = withContext(Dispatchers.IO) {
        ScriptSnapshot(FridaNative.sessionSnapshotScript(handle, embedScript, warmupScript ?: ""))
    }

    /**
     * Creates a script that starts from the pre-initialized state captured in [snapshot].
     * [source] is optional runtime JavaScript layered on top of the snapshot's heap.
     */
    suspend fun createScriptFromSnapshot(
        snapshot: ScriptSnapshot,
        source: String = ""
    ): FridaScript = withContext(Dispatchers.IO) {
        FridaScript(FridaNative.sessionCreateScriptFromSnapshot(handle, source, snapshot.bytes))
    }

    suspend fun detach() = withContext(Dispatchers.IO) {
        FridaNative.sessionDetach(handle)
        FridaNative.unref(handle)
    }
}
