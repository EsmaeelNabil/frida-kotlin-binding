package dev.supersam.frida

import java.nio.file.Files
import java.util.Locale

object NativeLoader {

    @Volatile private var loaded = false

    fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            loadNativeLibrary()
            loaded = true
        }
    }

    private fun loadNativeLibrary() {
        // In development, java.library.path points to the CMake build dir
        try {
            System.loadLibrary("frida_wrapper")
            return
        } catch (e: UnsatisfiedLinkError) {
            // fall through to classpath extraction
        }

        val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
        val (osDir, ext) = when {
            osName.contains("mac") -> "macos" to "dylib"
            osName.contains("linux") -> "linux" to "so"
            else -> throw UnsupportedOperationException("Unsupported OS: $osName")
        }

        val resourcePath = "/native/$osDir/libfrida_wrapper.$ext"
        val stream = NativeLoader::class.java.getResourceAsStream(resourcePath)
            ?: throw IllegalStateException("Native library not found at $resourcePath")

        val tempFile = Files.createTempFile("frida_wrapper", ".$ext").toFile()
        tempFile.deleteOnExit()
        stream.use { it.copyTo(tempFile.outputStream()) }
        System.load(tempFile.absolutePath)
    }
}
