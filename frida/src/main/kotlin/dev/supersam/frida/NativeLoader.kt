import java.nio.file.Files
import java.util.*

object NativeLoader {
    init {
        val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
        val osDir = when {
            osName.contains("mac") -> "macos"
            osName.contains("linux") -> "linux"
            osName.contains("windows") -> "windows"
            else -> throw UnsupportedOperationException("Unsupported operating system: $osName")
        }

        val libName = "libfrida_wrapper.dylib"
        val resourcePath = "/native/$osDir/$libName"

        val stream = NativeLoader::class.java.getResourceAsStream(resourcePath)
            ?: throw IllegalStateException("Could not find native library at $resourcePath")

        val tempFile = Files.createTempFile("frida_wrapper", ".dylib").toFile()
        tempFile.deleteOnExit()
        stream.use { it.copyTo(tempFile.outputStream()) }

        println("Loading library from: ${tempFile.absolutePath}")
        System.load(tempFile.absolutePath)
        println("Library loaded successfully")
    }

    fun load() {
        // The init block will handle the loading
    }
}