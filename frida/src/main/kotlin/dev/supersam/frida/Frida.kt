package dev.supersam.frida

import dev.supersam.fridaSource.FridaDeviceType
import dev.supersam.fridaSource.FridaScope
import dev.supersam.fridaSource.SWIGTYPE_p__FridaApplicationQueryOptions
import dev.supersam.fridaSource.frida

class Frida {
    companion object {
        init {
            NativeLoader.load()
            frida.frida_init()
        }

        private val manager by lazy {
            frida.frida_device_manager_new()
        }

        fun enumerateApplications(appId: String): List<Application> {
            val appsIdentifiers = mutableListOf<Application>()

            val device = frida.frida_device_manager_get_device_by_id_sync(manager, appId, 0)

            val option = frida.frida_application_query_options_new()
            frida.frida_application_query_options_set_scope(option, FridaScope.FRIDA_SCOPE_FULL)
            val apps = frida.frida_device_enumerate_applications_sync(device, option)
            val appsSize = frida.frida_application_list_size(apps)
            for (i in 0 until appsSize) {
                val app = frida.frida_application_list_get(apps, i)

                appsIdentifiers.add(
                    Application(
                        name = frida.frida_application_get_name(app),
                        identifier = frida.frida_application_get_identifier(app),
                        pid = frida.frida_application_get_pid(app)
                    )
                )
            }

            return appsIdentifiers
        }

        fun enumerateDevices(): List<Device> {
            val devices = mutableListOf<Device>()

            val deviceList = frida.frida_device_manager_enumerate_devices_sync(manager)
            val size = frida.frida_device_list_size(deviceList)
            for (i in 0 until size) {
                val device = frida.frida_device_list_get(deviceList, i)
                devices.add(
                    Device(
                        id = frida.frida_device_get_id(device),
                        name = frida.frida_device_get_name(device),
                        type = frida.frida_device_get_dtype(device)
                    )
                )
            }

            return devices
        }
    }

    data class Device(
        val id: String,
        val name: String,
        val type: FridaDeviceType = FridaDeviceType.FRIDA_DEVICE_TYPE_LOCAL
    )

    data class Application(
        val identifier: String,
        val name: String,
        val pid : Long
    )
}