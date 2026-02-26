package dev.supersam.frida

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

object Frida {

    private val deviceManagerHandle: Long by lazy {
        NativeLoader.load()
        FridaNative.fridaInit()
        FridaNative.deviceManagerNew()
    }

    /** Frida core version string, e.g. `"17.7.3"`. */
    val version: String get() = FridaNative.fridaVersionString()

    // ---- enumeration ----

    suspend fun enumerateDevices(): List<FridaDevice> = withContext(Dispatchers.IO) {
        FridaNative.deviceManagerEnumerateDevices(deviceManagerHandle)
            .map { FridaDevice(it) }
    }

    suspend fun getDevice(id: String, timeout: Int = 0): FridaDevice = withContext(Dispatchers.IO) {
        FridaDevice(FridaNative.deviceManagerGetDeviceById(deviceManagerHandle, id, timeout))
    }

    // ---- remote device pairing ----

    /**
     * Connects to a remote Frida server at [address] (e.g. `"192.168.1.10"` or
     * `"192.168.1.10:27042"`). Returns the [FridaDevice] for the remote host.
     */
    suspend fun addRemoteDevice(address: String): FridaDevice = withContext(Dispatchers.IO) {
        FridaDevice(FridaNative.deviceManagerAddRemoteDevice(deviceManagerHandle, address))
    }

    /**
     * Disconnects from the remote Frida server at [address]. The device previously
     * returned by [addRemoteDevice] becomes invalid after this call.
     */
    suspend fun removeRemoteDevice(address: String) = withContext(Dispatchers.IO) {
        FridaNative.deviceManagerRemoveRemoteDevice(deviceManagerHandle, address)
    }

    // ---- device list signals ----

    /** Emits a [FridaDevice] whenever a new device is connected. */
    val deviceAdded: Flow<FridaDevice> = callbackFlow {
        val cb = FridaNative.DeviceCallback { h -> trySend(FridaDevice(h)) }
        val cbHandle = FridaNative.deviceManagerConnectAdded(deviceManagerHandle, cb)
        awaitClose { FridaNative.deviceManagerDisconnectAdded(deviceManagerHandle, cbHandle) }
    }

    /** Emits a [FridaDevice] whenever a device is disconnected. */
    val deviceRemoved: Flow<FridaDevice> = callbackFlow {
        val cb = FridaNative.DeviceCallback { h -> trySend(FridaDevice(h)) }
        val cbHandle = FridaNative.deviceManagerConnectRemoved(deviceManagerHandle, cb)
        awaitClose { FridaNative.deviceManagerDisconnectRemoved(deviceManagerHandle, cbHandle) }
    }

    /**
     * Emits [Unit] whenever the device list changes (device added or removed).
     * Simpler alternative to collecting both [deviceAdded] and [deviceRemoved].
     */
    val deviceChanged: Flow<Unit> = callbackFlow {
        val cb = FridaNative.ChangedCallback { trySend(Unit) }
        val cbHandle = FridaNative.deviceManagerConnectChanged(deviceManagerHandle, cb)
        awaitClose { FridaNative.deviceManagerDisconnectChanged(deviceManagerHandle, cbHandle) }
    }

    // ---- lifecycle ----

    fun shutdown() {
        FridaNative.fridaShutdown()
    }
}
