package dev.supersam.frida

import frida_core.*
import kotlinx.cinterop.*
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@OptIn(ExperimentalForeignApi::class)
object Frida {
    private val deviceManager: CPointer<FridaDeviceManager> by lazy {
        frida_init()
        frida_device_manager_new()!!
    }

    val version: String
        get() = frida_version_string()?.toKString() ?: ""

    suspend fun enumerateDevices(): List<FridaDevice> = memScoped {
        val list = checkError { err ->
            frida_device_manager_enumerate_devices_sync(deviceManager, null, err.ptr)
        }

        val result = mutableListOf<FridaDevice>()
        val size = frida_device_list_size(list)
        for (i in 0 until size) {
            val dev = frida_device_list_get(list, i)
            if (dev != null) {
                result.add(FridaDevice(dev))
            }
        }
        g_object_unref(list)
        result
    }

    suspend fun getDevice(id: String, timeout: Int = 0): FridaDevice = memScoped {
        val dev = checkError { err ->
            frida_device_manager_get_device_by_id_sync(deviceManager, id, timeout, null, err.ptr)
        }
        FridaDevice(dev!!)
    }

    fun shutdown() {
        frida_shutdown()
        // g_object_unref(deviceManager) // lazy property cannot be uninitialized or reset easily
    }
}
