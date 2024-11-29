package dev.supersam.app

import dev.supersam.frida.FridaDeviceType
import dev.supersam.frida.enumerateApplications
import dev.supersam.frida.enumerateDevices

fun main() {
    enumerateDevices().forEach {
        if (it.type == FridaDeviceType.FRIDA_DEVICE_TYPE_USB) {
            it.enumerateApplications().forEach {
                println(it)
            }
        }
    }
}