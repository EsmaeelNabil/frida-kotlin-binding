package dev.supersam.Jnr

import dev.supersam.frida.FridaDeviceType
import jnr.ffi.Pointer

enum class FridaScope {
    FRIDA_SCOPE_MINIMAL,
    FRIDA_SCOPE_METADATA,
    FRIDA_SCOPE_FULL
}

interface FridaLibraryInterface {
    fun frida_init()
    fun frida_version_string(): String
    fun frida_device_manager_new(): Pointer

    //------------------------------ Device enumeration -------------------
    // returns : `FridaDeviceList *` -> Pointer
    fun frida_device_manager_enumerate_devices_sync(
        deviceManager: Pointer, // `FridaDeviceManager *` -> Pointer
        cancellable: Pointer?, // `GCancellable *` -> Pointer, nullable
        error: Pointer? // `GError **` -> Pointer, nullable
    ): Pointer

    // List Size
    fun frida_device_list_size(fridaDeviceList: Pointer): Int

    // FridaDevice *
    // FridaDevice * frida_device_list_get (FridaDeviceList * self, gint index);
    fun frida_device_list_get(fridaDeviceList: Pointer, index: Int): Pointer

    // const gchar * frida_device_get_id (FridaDevice * self);
    fun frida_device_get_id(fridaDevice: Pointer): String

    // const gchar * frida_device_get_name (FridaDevice * self);
    fun frida_device_get_name(fridaDevice: Pointer): String

    // TODO: Add support for variants
    // GVariant * frida_device_get_icon (FridaDevice * self);
    // fun frida_device_get_icon(fridaDevice: Pointer): String

    // FridaDeviceType frida_device_get_dtype (FridaDevice * self);
    fun frida_device_get_dtype(fridaDevice: Pointer): FridaDeviceType

    // FridaDeviceManager * frida_device_get_manager (FridaDevice * self);
    fun frida_device_get_manager(fridaDevice: Pointer): Pointer

    // FridaDevice * frida_device_manager_get_device_by_id_sync (FridaDeviceManager * self, const gchar * id, gint timeout, GCancellable * cancellable, GError ** error);
    fun frida_device_manager_get_device_by_id_sync(
        fridaDeviceManager: Pointer,
        deviceId: String,
        timeout: Int,
        cancellable: Pointer?,
        error: Pointer?
    ): Pointer

    //------------------------------ Applications enumeration -------------------
    // FridaApplicationList * frida_device_enumerate_applications_sync (FridaDevice * self, FridaApplicationQueryOptions * options, GCancellable * cancellable, GError ** error);
    fun frida_device_enumerate_applications_sync(
        fridaDevice: Pointer,
        fridaApplicationQueryOptions: Pointer,
        cancellable: Pointer?,
        error: Pointer?
    ): Pointer

    fun frida_application_query_options_new(): Pointer
    fun frida_application_query_options_get_scope(fridaApplicationQueryOptions: Pointer): Pointer
    fun frida_application_query_options_set_scope(fridaApplicationQueryOptions: Pointer, fridaScope: FridaScope);

    fun frida_application_list_size(fridaApplicationList: Pointer): Int
    fun frida_application_list_get(fridaApplicationList: Pointer, index: Int): Pointer
    fun frida_application_get_identifier(fridaApplication: Pointer): String
    fun frida_application_get_name(fridaApplication: Pointer): String
    fun frida_application_get_pid(fridaApplication: Pointer): Int

}