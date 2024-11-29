package dev.supersam.frida

import dev.supersam.Jnr.FridaLibraryInterface
import dev.supersam.frida.FridaLib.frida
import jnr.ffi.LibraryLoader
import jnr.ffi.Pointer


object FridaLib {

    var frida: FridaLibraryInterface

    init {
        val libraryPath = FridaLib::class.java
            .getResource("/libfrida-core.dylib")
            ?.file

        frida = LibraryLoader.create(FridaLibraryInterface::class.java)
            .load(libraryPath)

        frida.frida_init()
    }
}


fun enumerateDevices(): List<FridaDevice> = with(frida) {
    val devices = mutableListOf<FridaDevice>()
    val manager = frida_device_manager_new()
    val devicesListPointer = frida_device_manager_enumerate_devices_sync(
        manager,
        null,
        null
    )
    val devicesListSize = frida_device_list_size(devicesListPointer)
    for (i in 0 until devicesListSize) {
        val devicePointer = frida_device_list_get(devicesListPointer, i)
        devices.add(
            FridaDevice(
                name = frida_device_get_name(devicePointer),
                id = frida_device_get_id(devicePointer),
                type = frida_device_get_dtype(devicePointer)
            )
        )
    }
    return devices
}


fun getDevicePointerBy(deviceId: String): Pointer = with(frida) {
    return frida_device_manager_get_device_by_id_sync(
        frida_device_manager_new(),
        deviceId,
        0,
        null,
        null
    )
}

fun FridaDevice.getDevicePointer(): Pointer = with(frida) {
    return frida_device_manager_get_device_by_id_sync(
        frida_device_manager_new(),
        id,
        0,
        null,
        null
    )
}