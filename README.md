# frida-kotlin-binding

A Kotlin/JVM binding for [Frida](https://frida.re) built on hand-written JNI — no SWIG, no code generation. Exposes a coroutine-first API for device enumeration, process attachment, script injection, and live message streaming.

## Modules

| Module | Description |
|--------|-------------|
| `:frida` | JVM controller library — connects to Frida-enabled devices, injects scripts |
| `:app` | Desktop sample app |
| `:frida-gadget` | Android AAR — bundles `libfrida-gadget.so` for embedding Frida in Android apps |

## Quick start

```bash
# Build native layer (downloads devkit if needed, compiles frida_jni.cpp)
./gradlew :frida:buildNative

# Run the sample app (lists devices + apps)
./gradlew :app:run
```

## API overview

```kotlin
import dev.supersam.frida.*
import kotlinx.coroutines.*

fun main() = runBlocking {
    // Enumerate all connected devices
    val devices = Frida.enumerateDevices()

    // Grab a USB device
    val device = devices.first { it.type == DeviceType.USB }

    // List running apps
    val apps = device.enumerateApplications(scope = Scope.FULL)

    // Attach to a running process by PID or bundle ID
    val session = device.attach("com.example.target")

    // Inject a Frida script
    val script = session.createScript("""
        Java.perform(() => {
            console.log("Hello from Frida!");
        });
    """.trimIndent())

    // Collect messages from the script as a Flow
    launch {
        script.messages.collect { json -> println("Message: $json") }
    }

    script.load()

    // Send a message to the script
    script.post("""{"type":"ping"}""")

    delay(5_000)
    script.unload()
    session.detach()
    Frida.shutdown()
}
```

## Platform support

| Platform | Controller (`:frida`) | Gadget (`:frida-gadget`) |
|----------|-----------------------|--------------------------|
| macOS arm64 | ✅ | — |
| macOS x86_64 | ✅ (devkit auto-downloaded) | — |
| Linux x86_64 | ✅ (devkit auto-downloaded) | — |
| Android arm64-v8a | — | ✅ |
| Android armeabi-v7a | — | ✅ |
| Android x86_64 | — | ✅ |
| Android x86 | — | ✅ |

## Building the native layer

The native library (`libfrida_wrapper.dylib` / `.so`) is compiled from `frida_jni.cpp` via CMake.

```bash
# Download Frida 17.7.3 devkit for the host platform (skips if already present)
./gradlew :frida:downloadFridaDevkit

# Compile frida_jni.cpp → libfrida_wrapper.dylib
./gradlew :frida:buildNative

# Copy the dylib into the JAR resources tree
./gradlew :frida:copyNativeLib
```

## Android gadget AAR

```bash
# Download all 4 ABI .so files from GitHub releases
cd frida-gadget && bash download_gadget.sh

# Build the AAR
./gradlew :frida-gadget:assembleRelease
```

Usage in an Android app:

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        FridaGadget.load()   // exposes port 27042 by default
    }
}
```

## Implemented API surface

- [x] Enumerate devices
- [x] Get device by ID
- [x] Frida version string (`Frida.version`)
- [x] Enumerate applications (with scope: MINIMAL / METADATA / FULL)
- [x] Enumerate processes (with scope)
- [x] Attach to process by PID
- [x] Attach to process by bundle ID (convenience)
- [x] Spawn a program
- [x] Resume a PID
- [x] Create and load a script
- [x] Unload a script
- [x] Send messages to a script (`script.post`)
- [x] Receive messages from a script (`script.messages: Flow<String>`)
- [x] Session detached signal (`session.detached: Flow<DetachReason>`)
- [x] Script destroyed signal (`script.destroyed: Flow<Unit>`)
- [x] Script message parsing (`script.typedMessages: Flow<ScriptMessage>`)
- [x] Script snapshots — `session.snapshotScript()` / `session.createScriptFromSnapshot()`
- [x] Spawn gating — `enableSpawnGating()` + `childAdded / childRemoved: Flow<FridaChild>`
- [x] Device manager signals — `Frida.deviceAdded / deviceRemoved / deviceChanged: Flow<…>`
- [x] Remote device pairing — `Frida.addRemoteDevice(address)` / `removeRemoteDevice(address)`
- [x] Session reconnect — `device.attachWithReconnect(pid) { session -> … }`
- [x] Interceptor helpers — `session.interceptor.attach(address, onEnter, onLeave)`
- [x] Stalker helpers — `session.stalker.follow(threadId, events, onReceive)`
- [x] Android gadget AAR

## Roadmap

- [ ] iOS support (macOS controller already works; devkit is the same)
- [ ] Device system parameters (`frida_device_query_system_parameters_sync`)
- [ ] Process output signal (`"output"` on `FridaDevice`)

## Project structure

```
frida-kotlin-binding/
├── frida/
│   ├── src/main/cpp/
│   │   ├── frida_jni.cpp          ← JNI bridge (all native ↔ JVM calls)
│   │   ├── CMakeLists.txt
│   │   ├── frida_core.h           ← Frida 17.7.3 devkit header
│   │   ├── libfrida-core.a        ← Frida 17.7.3 static library
│   │   └── download_frida_devkit.sh
│   └── src/main/kotlin/dev/supersam/frida/
│       ├── FridaNative.kt         ← internal JNI declarations
│       ├── FridaModels.kt         ← data classes and enums
│       ├── Frida.kt               ← top-level entry point
│       ├── FridaDevice.kt
│       ├── FridaSession.kt
│       ├── FridaScript.kt
│       ├── FridaInterceptor.kt    ← Interceptor + Stalker helpers
│       ├── FridaReconnect.kt      ← attachWithReconnect extension
│       └── NativeLoader.kt
├── frida-gadget/
│   ├── download_gadget.sh
│   └── src/main/
│       ├── jniLibs/<abi>/libfrida-gadget.so   ← downloaded by script
│       └── kotlin/.../FridaGadget.kt
├── app/
│   └── src/main/kotlin/dev/supersam/app/App.kt
└── docs/
    ├── ARCHITECTURE.md            ← how the layers fit together
    └── EXTENDING.md               ← how to add new API surface
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for a deep dive into the layered design, and [`docs/EXTENDING.md`](docs/EXTENDING.md) for a step-by-step guide to adding new Frida API.
