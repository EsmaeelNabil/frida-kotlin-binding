package dev.supersam.frida

import kotlinx.coroutines.flow.Flow

interface IFridaDevice {
    val id: String
    val name: String
    val type: DeviceType

    suspend fun enumerateApplications(scope: Scope = Scope.MINIMAL): List<Application>
    suspend fun enumerateProcesses(scope: Scope = Scope.MINIMAL): List<Process>

    suspend fun attach(pid: Int): IFridaSession
    suspend fun attach(bundleId: String): IFridaSession

    suspend fun spawn(program: String): Int
    suspend fun resume(pid: Int)

    /**
     * Enable spawn gating so that child processes are paused on creation.
     * After calling this, collect [childAdded] to receive each new child,
     * then call [resume] on the child's PID to continue execution.
     */
    suspend fun enableSpawnGating()
    suspend fun disableSpawnGating()

    /** Returns all children currently paused by spawn gating. */
    suspend fun enumeratePendingChildren(): List<FridaChild>

    /**
     * Emits every time a new child process is created (requires [enableSpawnGating]).
     * The child is paused — call [resume] with its PID to let it continue.
     */
    val childAdded: Flow<FridaChild>

    /**
     * Emits every time a previously-gated child is removed (resumed or killed).
     */
    val childRemoved: Flow<FridaChild>
}

interface IFridaSession {
    /**
     * Emits once when the session is detached, then closes.
     * Backed by the Frida "detached" GObject signal.
     */
    val detached: Flow<DetachReason>

    suspend fun createScript(source: String): IFridaScript

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
    ): ScriptSnapshot

    /**
     * Creates a script that starts from the pre-initialized state captured in [snapshot].
     * [source] is optional runtime JavaScript layered on top of the snapshot's heap.
     */
    suspend fun createScriptFromSnapshot(
        snapshot: ScriptSnapshot,
        source: String = ""
    ): IFridaScript

    suspend fun detach()
}

interface IFridaScript {
    /**
     * Raw JSON strings from the script's send() / console calls.
     * Backed by the Frida "message" GObject signal.
     * Use [typedMessages] for parsed [ScriptMessage] values.
     */
    val messages: Flow<String>

    /**
     * Parsed version of [messages]. Each raw JSON string is decoded into
     * a [ScriptMessage.Send], [ScriptMessage.Error], [ScriptMessage.Log],
     * or [ScriptMessage.Raw] for unknown types.
     */
    val typedMessages: Flow<ScriptMessage>

    /**
     * Emits [Unit] once when the script is destroyed (e.g. due to an uncaught exception
     * that crashed the runtime, or when the session is detached), then closes.
     * Backed by the Frida "destroyed" GObject signal.
     */
    val destroyed: Flow<Unit>

    suspend fun load()
    suspend fun unload()

    /** Send a JSON message to the script (received via script.recv() / rpc.exports). */
    fun post(message: String)
}
