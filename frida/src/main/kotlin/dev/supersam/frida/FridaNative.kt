package dev.supersam.frida

/**
 * Internal JNI bridge — all Frida C API calls go through here.
 * Pointer handles are carried as jlong (intptr_t casts in C++).
 */
internal object FridaNative {

    // Callback interfaces called back from the native GLib signal thread
    fun interface DetachedCallback {
        fun onDetached(reason: Int)
    }

    fun interface MessageCallback {
        fun onMessage(message: String)
    }

    /** Used for child-added and child-removed on FridaDevice. */
    fun interface ChildCallback {
        fun onChild(handle: Long)
    }

    /** Used for device added/removed on FridaDeviceManager. */
    fun interface DeviceCallback {
        fun onDevice(handle: Long)
    }

    /** Used for the device-list "changed" notification. */
    fun interface ChangedCallback {
        fun onChange()
    }

    /** Used for the script "destroyed" notification. */
    fun interface DestroyedCallback {
        fun onDestroyed()
    }

    // Init
    @JvmStatic external fun fridaInit()
    @JvmStatic external fun fridaShutdown()
    @JvmStatic external fun fridaVersionString(): String

    // DeviceManager — enumeration
    @JvmStatic external fun deviceManagerNew(): Long
    @JvmStatic external fun deviceManagerEnumerateDevices(handle: Long): LongArray
    @JvmStatic external fun deviceManagerGetDeviceById(handle: Long, id: String, timeout: Int): Long

    // DeviceManager — remote pairing
    @JvmStatic external fun deviceManagerAddRemoteDevice(handle: Long, address: String): Long
    @JvmStatic external fun deviceManagerRemoveRemoteDevice(handle: Long, address: String)

    // DeviceManager — signals
    @JvmStatic external fun deviceManagerConnectAdded(handle: Long, cb: DeviceCallback): Long
    @JvmStatic external fun deviceManagerDisconnectAdded(handle: Long, cbHandle: Long)
    @JvmStatic external fun deviceManagerConnectRemoved(handle: Long, cb: DeviceCallback): Long
    @JvmStatic external fun deviceManagerDisconnectRemoved(handle: Long, cbHandle: Long)
    @JvmStatic external fun deviceManagerConnectChanged(handle: Long, cb: ChangedCallback): Long
    @JvmStatic external fun deviceManagerDisconnectChanged(handle: Long, cbHandle: Long)

    // Device — getters
    @JvmStatic external fun deviceGetId(handle: Long): String
    @JvmStatic external fun deviceGetName(handle: Long): String
    @JvmStatic external fun deviceGetType(handle: Long): Int

    // Device — enumeration
    @JvmStatic external fun deviceEnumerateApplications(handle: Long, scope: Int): LongArray
    @JvmStatic external fun deviceEnumerateProcesses(handle: Long, scope: Int): LongArray

    // Device — process control
    @JvmStatic external fun deviceAttach(handle: Long, pid: Int): Long
    @JvmStatic external fun deviceSpawn(handle: Long, program: String): Int
    @JvmStatic external fun deviceResume(handle: Long, pid: Int)

    // Device — spawn gating
    @JvmStatic external fun deviceEnableSpawnGating(handle: Long)
    @JvmStatic external fun deviceDisableSpawnGating(handle: Long)
    @JvmStatic external fun deviceEnumeratePendingChildren(handle: Long): LongArray

    // Device — child signals
    @JvmStatic external fun deviceConnectChildAdded(handle: Long, cb: ChildCallback): Long
    @JvmStatic external fun deviceDisconnectChildAdded(handle: Long, cbHandle: Long)
    @JvmStatic external fun deviceConnectChildRemoved(handle: Long, cb: ChildCallback): Long
    @JvmStatic external fun deviceDisconnectChildRemoved(handle: Long, cbHandle: Long)

    // Application
    @JvmStatic external fun applicationGetIdentifier(handle: Long): String
    @JvmStatic external fun applicationGetName(handle: Long): String
    @JvmStatic external fun applicationGetPid(handle: Long): Int

    // Process
    @JvmStatic external fun processGetPid(handle: Long): Int
    @JvmStatic external fun processGetName(handle: Long): String

    // Child
    @JvmStatic external fun childGetPid(handle: Long): Int
    @JvmStatic external fun childGetParentPid(handle: Long): Int
    @JvmStatic external fun childGetOrigin(handle: Long): Int
    @JvmStatic external fun childGetIdentifier(handle: Long): String?
    @JvmStatic external fun childGetPath(handle: Long): String?

    // Session
    @JvmStatic external fun sessionCreateScript(handle: Long, source: String): Long
    @JvmStatic external fun sessionDetach(handle: Long)
    @JvmStatic external fun sessionConnectDetached(handle: Long, callback: DetachedCallback): Long
    @JvmStatic external fun sessionDisconnectDetached(handle: Long, cbHandle: Long)

    // Script
    @JvmStatic external fun scriptLoad(handle: Long)
    @JvmStatic external fun scriptUnload(handle: Long)
    @JvmStatic external fun scriptPost(handle: Long, message: String)
    @JvmStatic external fun scriptConnectMessage(handle: Long, callback: MessageCallback): Long
    @JvmStatic external fun scriptDisconnectMessage(handle: Long, cbHandle: Long)
    @JvmStatic external fun scriptConnectDestroyed(handle: Long, callback: DestroyedCallback): Long
    @JvmStatic external fun scriptDisconnectDestroyed(handle: Long, cbHandle: Long)

    // Session — snapshots
    @JvmStatic external fun sessionSnapshotScript(handle: Long, embedScript: String, warmupScript: String): ByteArray
    @JvmStatic external fun sessionCreateScriptFromSnapshot(handle: Long, source: String, snapshot: ByteArray): Long

    // Memory management
    @JvmStatic external fun unref(handle: Long)
}
