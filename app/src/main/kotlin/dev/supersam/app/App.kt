package dev.supersam.app

import dev.supersam.frida.Frida
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val devices = Frida.enumerateDevices()
    println("Found ${devices.size} device(s):")

    for (device in devices) {
        println("  Device: ${device.name} (${device.id}) type=${device.type}")


            try {
                val apps = device.enumerateApplications()
                println("  Applications (${apps.size}):")
                for (app in apps) {
                    println("    ${app.name} [${app.identifier}] pid=${app.pid}")
                }
            } catch (e: Exception) {
                println("  Error enumerating applications: ${e.message}")
            }

    }


    Frida.shutdown()
}
