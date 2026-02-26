package dev.supersam.frida

import frida_core.*
import kotlinx.cinterop.*
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@OptIn(ExperimentalForeignApi::class)
class FridaDevice(val handle: CPointer<FridaDevice>) : IFridaDevice {

    override val id: String
        get() = frida_device_get_id(handle)?.toKString() ?: ""

    override val name: String
        get() = frida_device_get_name(handle)?.toKString() ?: ""

    override val type: DeviceType
        get() = when (frida_device_get_dtype(handle)) {
            FRIDA_DEVICE_TYPE_LOCAL -> DeviceType.LOCAL
            FRIDA_DEVICE_TYPE_REMOTE -> DeviceType.REMOTE
            FRIDA_DEVICE_TYPE_USB -> DeviceType.USB
            else -> DeviceType.LOCAL
        }

    override suspend fun enumerateApplications(scope: Scope): List<Application> = memScoped {
        val opts = frida_application_query_options_new()
        frida_application_query_options_set_scope(opts, scope.value.toUInt().convert())

        val list = checkError { err ->
            frida_device_enumerate_applications_sync(handle, opts, null, err.ptr)
        }
        g_object_unref(opts)

        val result = mutableListOf<Application>()
        val size = frida_application_list_size(list)
        for (i in 0 until size) {
            val app = frida_application_list_get(list, i)
            if (app != null) {
                result.add(Application(
                    identifier = frida_application_get_identifier(app)?.toKString() ?: "",
                    name = frida_application_get_name(app)?.toKString() ?: "",
                    pid = frida_application_get_pid(app).toInt()
                ))
            }
        }
        g_object_unref(list)
        result
    }

    override suspend fun enumerateProcesses(scope: Scope): List<Process> = memScoped {
        val opts = frida_process_query_options_new()
        frida_process_query_options_set_scope(opts, scope.value.toUInt().convert())

        val list = checkError { err ->
            frida_device_enumerate_processes_sync(handle, opts, null, err.ptr)
        }
        g_object_unref(opts)

        val result = mutableListOf<Process>()
        val size = frida_process_list_size(list)
        for (i in 0 until size) {
            val proc = frida_process_list_get(list, i)
            if (proc != null) {
                result.add(Process(
                    pid = frida_process_get_pid(proc).toInt(),
                    name = frida_process_get_name(proc)?.toKString() ?: ""
                ))
            }
        }
        g_object_unref(list)
        result
    }

    override suspend fun attach(pid: Int): FridaSession = memScoped {
        val session = checkError { err ->
            frida_device_attach_sync(handle, pid.toUInt(), null, null, err.ptr)
        }
        FridaSession(session!!)
    }

    override suspend fun attach(bundleId: String): FridaSession {
        val apps = enumerateApplications()
        val app = apps.firstOrNull { it.identifier == bundleId }
            ?: throw IllegalArgumentException("Application not found: $bundleId")
        return attach(app.pid)
    }

    override suspend fun spawn(program: String): Int = memScoped {
        val pid = checkError { err ->
            frida_device_spawn_sync(handle, program, null, null, err.ptr)
        }
        pid.toInt()
    }

    override suspend fun resume(pid: Int) = memScoped {
        checkError { err ->
            frida_device_resume_sync(handle, pid.toUInt(), null, err.ptr)
        }
    }

    override suspend fun enableSpawnGating() = memScoped {
         checkError { err ->
             frida_device_enable_spawn_gating_sync(handle, null, err.ptr)
         }
    }

    override suspend fun disableSpawnGating() = memScoped {
         checkError { err ->
             frida_device_disable_spawn_gating_sync(handle, null, err.ptr)
         }
    }

    override suspend fun enumeratePendingChildren(): List<FridaChild> = memScoped {
        val list = checkError { err ->
            frida_device_enumerate_pending_children_sync(handle, null, err.ptr)
        }

        val result = mutableListOf<FridaChild>()
        val size = frida_child_list_size(list)
        for (i in 0 until size) {
            val child = frida_child_list_get(list, i)
            if (child != null) {
                result.add(readChild(child))
            }
        }
        g_object_unref(list)
        result
    }

    override val childAdded: Flow<FridaChild> = callbackFlow {
        val ref = StableRef.create(this)
        val handlerId = g_signal_connect_data(
            handle,
            "child-added",
            staticCFunction(::onChildAdded),
            ref.asCPointer(),
            staticCFunction(::onDestroyRef),
            0u
        )
        awaitClose {
            g_signal_handler_disconnect(handle, handlerId)
        }
    }

    override val childRemoved: Flow<FridaChild> = callbackFlow {
        val ref = StableRef.create(this)
        val handlerId = g_signal_connect_data(
            handle,
            "child-removed",
            staticCFunction(::onChildRemoved),
            ref.asCPointer(),
            staticCFunction(::onDestroyRef),
            0u
        )
        awaitClose {
            g_signal_handler_disconnect(handle, handlerId)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun readChild(h: CPointer<FridaChild>): FridaChild {
    return FridaChild(
        pid = frida_child_get_pid(h).toInt(),
        parentPid = frida_child_get_parent_pid(h).toInt(),
        origin = ChildOrigin.fromInt(frida_child_get_origin(h).toInt()),
        identifier = frida_child_get_identifier(h)?.toKString(),
        path = frida_child_get_path(h)?.toKString()
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun onChildAdded(device: CPointer<FridaDevice>?, child: CPointer<FridaChild>?, userData: COpaquePointer?) {
    if (userData == null || child == null) return
    val scope = userData.asStableRef<ProducerScope<FridaChild>>().get()
    scope.trySend(readChild(child))
}

@OptIn(ExperimentalForeignApi::class)
private fun onChildRemoved(device: CPointer<FridaDevice>?, child: CPointer<FridaChild>?, userData: COpaquePointer?) {
    if (userData == null || child == null) return
    val scope = userData.asStableRef<ProducerScope<FridaChild>>().get()
    scope.trySend(readChild(child))
}
