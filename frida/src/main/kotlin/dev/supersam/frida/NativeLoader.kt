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

        val libraryPath = NativeLoader::class.java
            .getResource("/native/$osDir/libfrida_wrapper.dylib")
            ?.file
            ?: throw IllegalStateException("Could not find native library")

        println("Loading library from: $libraryPath")
        System.load(libraryPath)
        println("Library loaded successfully")
    }

    fun load() {
        // The init block will handle the loading
    }
}