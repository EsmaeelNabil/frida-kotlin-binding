package dev.supersam.frida.gadget

/**
 * Loads the Frida gadget shared library bundled in this AAR.
 *
 * The gadget `.so` files must be placed under:
 *   src/main/jniLibs/<abi>/libfrida-gadget.so
 *
 * Download them from the Frida GitHub releases page:
 *   https://github.com/frida/frida/releases/tag/17.7.3
 *   - frida-gadget-17.7.3-android-arm64.so  → jniLibs/arm64-v8a/libfrida-gadget.so
 *   - frida-gadget-17.7.3-android-arm.so    → jniLibs/armeabi-v7a/libfrida-gadget.so
 *   - frida-gadget-17.7.3-android-x86_64.so → jniLibs/x86_64/libfrida-gadget.so
 *   - frida-gadget-17.7.3-android-x86.so    → jniLibs/x86/libfrida-gadget.so
 *
 * Gadget mode is controlled by a config file at:
 *   /data/local/tmp/re.frida.Gadget.config  (or via assets)
 *
 * Default behavior (no config): listen on TCP port 27042.
 */
object FridaGadget {

    @Volatile private var loaded = false

    /**
     * Load the gadget into the current process.
     * Call this as early as possible in Application.onCreate() or a static initializer.
     */
    fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            System.loadLibrary("frida-gadget")
            loaded = true
        }
    }
}
