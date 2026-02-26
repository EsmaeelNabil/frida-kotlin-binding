package dev.supersam.frida

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

class FridaDevice(val handle: Long) {

    val id: String get() = FridaNative.deviceGetId(handle)
    val name: String get() = FridaNative.deviceGetName(handle)
    val type: DeviceType get() = DeviceType.fromInt(FridaNative.deviceGetType(handle))

    // ---- enumeration ----

    suspend fun enumerateApplications(scope: Scope = Scope.MINIMAL): List<Application> =
        withContext(Dispatchers.IO) {
            FridaNative.deviceEnumerateApplications(handle, scope.value).map { h ->
                Application(
                    identifier = FridaNative.applicationGetIdentifier(h),
                    name       = FridaNative.applicationGetName(h),
                    pid        = FridaNative.applicationGetPid(h)
                ).also { FridaNative.unref(h) }
            }
        }

    suspend fun enumerateProcesses(scope: Scope = Scope.MINIMAL): List<Process> =
        withContext(Dispatchers.IO) {
            FridaNative.deviceEnumerateProcesses(handle, scope.value).map { h ->
                Process(
                    pid  = FridaNative.processGetPid(h),
                    name = FridaNative.processGetName(h)
                ).also { FridaNative.unref(h) }
            }
        }

    // ---- process control ----

    suspend fun attach(pid: Int): FridaSession = withContext(Dispatchers.IO) {
        FridaSession(FridaNative.deviceAttach(handle, pid))
    }

    suspend fun attach(bundleId: String): FridaSession {
        val app = enumerateApplications().firstOrNull { it.identifier == bundleId }
            ?: throw IllegalArgumentException("Application not found: $bundleId")
        return attach(app.pid)
    }

    suspend fun spawn(program: String): Int = withContext(Dispatchers.IO) {
        FridaNative.deviceSpawn(handle, program)
    }

    suspend fun resume(pid: Int) = withContext(Dispatchers.IO) {
        FridaNative.deviceResume(handle, pid)
    }

    // ---- spawn gating ----

    /**
     * Enable spawn gating so that child processes are paused on creation.
     * After calling this, collect [childAdded] to receive each new child,
     * then call [resume] on the child's PID to continue execution.
     */
    suspend fun enableSpawnGating() = withContext(Dispatchers.IO) {
        FridaNative.deviceEnableSpawnGating(handle)
    }

    suspend fun disableSpawnGating() = withContext(Dispatchers.IO) {
        FridaNative.deviceDisableSpawnGating(handle)
    }

    /** Returns all children currently paused by spawn gating. */
    suspend fun enumeratePendingChildren(): List<FridaChild> = withContext(Dispatchers.IO) {
        FridaNative.deviceEnumeratePendingChildren(handle).map { h ->
            readChild(h).also { FridaNative.unref(h) }
        }
    }

    // ---- child signals ----

    /**
     * Emits every time a new child process is created (requires [enableSpawnGating]).
     * The child is paused — call [resume] with its PID to let it continue.
     */
    val childAdded: Flow<FridaChild> = callbackFlow {
        val cb = FridaNative.ChildCallback { h ->
            trySend(readChild(h).also { FridaNative.unref(h) })
        }
        val cbHandle = FridaNative.deviceConnectChildAdded(handle, cb)
        awaitClose { FridaNative.deviceDisconnectChildAdded(handle, cbHandle) }
    }

    /**
     * Emits every time a previously-gated child is removed (resumed or killed).
     */
    val childRemoved: Flow<FridaChild> = callbackFlow {
        val cb = FridaNative.ChildCallback { h ->
            trySend(readChild(h).also { FridaNative.unref(h) })
        }
        val cbHandle = FridaNative.deviceConnectChildRemoved(handle, cb)
        awaitClose { FridaNative.deviceDisconnectChildRemoved(handle, cbHandle) }
    }

    // ---- helpers ----

    private fun readChild(h: Long) = FridaChild(
        pid        = FridaNative.childGetPid(h),
        parentPid  = FridaNative.childGetParentPid(h),
        origin     = ChildOrigin.fromInt(FridaNative.childGetOrigin(h)),
        identifier = FridaNative.childGetIdentifier(h),
        path       = FridaNative.childGetPath(h)
    )

    override fun toString(): String = "FridaDevice(id=$id, name=$name, type=$type)"
}
