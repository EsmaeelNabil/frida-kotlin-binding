package dev.supersam.frida

import dev.supersam.Jnr.FridaLibraryInterface
import dev.supersam.Jnr.FridaScope

data class FridaDevice(
    val name: String,
    val id: String,
    val type: FridaDeviceType
)

fun FridaDevice.enumerateApplications(
    frida: FridaLibraryInterface = FridaLib.frida
): List<FridaApplication> = with(frida) {

    val list = mutableListOf<FridaApplication>()

    val devicePointer = getDevicePointer()

    // set scope
    val scope = FridaScope.FRIDA_SCOPE_FULL
    val queryOptions = frida_application_query_options_new()
    frida_application_query_options_set_scope(queryOptions, scope)

    val applicationsListPointer = frida_device_enumerate_applications_sync(
        devicePointer,
        queryOptions,
        null,
        null
    )

    val applicationsListSize = frida_application_list_size(applicationsListPointer)
    for (i in 0 until applicationsListSize) {
        val applicationPointer = frida_application_list_get(applicationsListPointer, i)
        list.add(
            FridaApplication(
                name = frida_application_get_name(applicationPointer),
                identifier = frida_application_get_identifier(applicationPointer),
                pid = frida_application_get_pid(applicationPointer)
            )
        )
    }

    return list
}

