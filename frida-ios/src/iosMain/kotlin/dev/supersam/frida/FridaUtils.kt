package dev.supersam.frida

import kotlinx.cinterop.*
import frida_core.*

@OptIn(ExperimentalForeignApi::class)
internal inline fun <T> checkError(block: (CPointerVar<GError>) -> T): T = memScoped {
    val error = alloc<CPointerVar<GError>>()
    val result = block(error)
    val err = error.value
    if (err != null) {
        val msg = err.pointed.message?.toKString() ?: "Unknown error"
        g_error_free(err)
        throw RuntimeException(msg)
    }
    result
}

@OptIn(ExperimentalForeignApi::class)
internal fun onDestroyRef(userData: COpaquePointer?, closure: CPointer<GClosure>?) {
    if (userData != null) {
        userData.asStableRef<Any>().dispose()
    }
}
