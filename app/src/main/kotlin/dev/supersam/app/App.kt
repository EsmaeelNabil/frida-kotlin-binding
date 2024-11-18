package dev.supersam.app

import NativeLoader
import dev.supersam.frida.Frida
import dev.supersam.fridaSource.FridaDeviceType

fun main() {
    NativeLoader.load()

    Frida.enumerateDevices().forEach {
        println("Device: ${it.name} (${it.id})")
        if (it.type == FridaDeviceType.FRIDA_DEVICE_TYPE_USB)
            try {
                Frida.enumerateApplications(it.id).forEach {
                    println("$it")
                }
            } catch (e: Exception) {
                println("Error: ${e.message}")
            }
    }
}