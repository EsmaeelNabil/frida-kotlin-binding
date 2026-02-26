# Extending the binding

This guide walks through every layer you need to touch when adding new Frida API surface. Read `ARCHITECTURE.md` first if you haven't already.

---

## The four-step pattern

Every new piece of Frida API follows the same path:

```
frida_core.h          (verify the C function signature)
    ↓
frida_jni.cpp         (implement the JNI function)
    ↓
FridaNative.kt        (declare the external fun)
    ↓
FridaDevice / FridaSession / FridaScript / Frida  (wrap in suspend fn or Flow)
```

---

## Step 1 — Find the C API in frida_core.h

The header is at `frida/src/main/cpp/frida_core.h`. Use grep to find the exact signature before writing anything:

```bash
grep "frida_session_compile_script" frida/src/main/cpp/frida_core.h
grep "frida_device_query_system_parameters" frida/src/main/cpp/frida_core.h
```

Key things to note:
- Does it take `GCancellable*`? Always pass `nullptr`.
- Does it take `GError**`? Always pass `&error` and check after the call.
- Does it take an options struct? Create it, set fields, then `frida_unref` after the sync call.
- Is it a signal (not a function)? See the **Adding a new signal** section below.

---

## Step 2 — Add the JNI function to frida_jni.cpp

JNI function name format: `Java_<package>_<class>_<methodName>`

Full prefix for this project: `Java_dev_supersam_frida_FridaNative_`

Add new functions before the closing `} // extern "C"` at the bottom of the file.

### Sync call returning a value

```cpp
JNIEXPORT jlong JNICALL
Java_dev_supersam_frida_FridaNative_deviceQuerySystemParameters(
        JNIEnv* env, jclass cls, jlong handle) {
    FridaDevice* device = (FridaDevice*)(intptr_t)handle;
    GError* error = nullptr;
    GHashTable* params = frida_device_query_system_parameters_sync(device, nullptr, &error);
    if (error) { throw_from_gerror(env, error); return 0; }
    return (jlong)(intptr_t)params;
}
```

### Sync call returning a string

```cpp
JNIEXPORT jstring JNICALL
Java_dev_supersam_frida_FridaNative_scriptGetName(
        JNIEnv* env, jclass cls, jlong handle) {
    const gchar* name = frida_script_options_get_name((FridaScriptOptions*)(intptr_t)handle);
    return name ? env->NewStringUTF(name) : nullptr;
}
```

### Sync call taking a string argument

```cpp
JNIEXPORT void JNICALL
Java_dev_supersam_frida_FridaNative_scriptSetName(
        JNIEnv* env, jclass cls, jlong handle, jstring jname) {
    const char* name = env->GetStringUTFChars(jname, nullptr);
    frida_script_options_set_name((FridaScriptOptions*)(intptr_t)handle, name);
    env->ReleaseStringUTFChars(jname, name);
}
```

### Sync call returning an array of handles

```cpp
// Pattern: used by deviceEnumerateApplications, deviceEnumerateProcesses,
//          deviceEnumeratePendingChildren
JNIEXPORT jlongArray JNICALL
Java_dev_supersam_frida_FridaNative_deviceEnumeratePendingChildren(
        JNIEnv* env, jclass cls, jlong handle) {
    GError* error = nullptr;
    FridaChildList* list = frida_device_enumerate_pending_children_sync(
            (FridaDevice*)(intptr_t)handle, nullptr, &error);
    if (error) { throw_from_gerror(env, error); return nullptr; }

    gint size = frida_child_list_size(list);
    jlongArray result = env->NewLongArray(size);
    jlong* elems = env->GetLongArrayElements(result, nullptr);
    for (gint i = 0; i < size; i++) {
        FridaChild* child = frida_child_list_get(list, i);
        g_object_ref(child);   // bump ref so handle stays valid after list is unref'd
        elems[i] = (jlong)(intptr_t)child;
    }
    env->ReleaseLongArrayElements(result, elems, 0);
    frida_unref(list);
    return result;
}
```

---

## Step 3 — Declare in FridaNative.kt

Add one line per JNI function in the matching section:

```kotlin
// Device
@JvmStatic external fun deviceQuerySystemParameters(handle: Long): Long
@JvmStatic external fun deviceEnumeratePendingChildren(handle: Long): LongArray
```

If the new function needs a signal callback, also declare a `fun interface`:

```kotlin
// New callback type (method name must match what C++ looks up via GetMethodID)
fun interface ChildCallback {
    fun onChild(handle: Long)    // → GetMethodID(cls, "onChild", "(J)V")
}

@JvmStatic external fun deviceConnectChildAdded(handle: Long, cb: ChildCallback): Long
@JvmStatic external fun deviceDisconnectChildAdded(handle: Long, cbHandle: Long)
```

---

## Step 4 — Wrap in the Kotlin API class

### Suspend function

```kotlin
// In FridaDevice.kt
suspend fun enumeratePendingChildren(): List<FridaChild> = withContext(Dispatchers.IO) {
    FridaNative.deviceEnumeratePendingChildren(handle).map { h ->
        FridaChild(
            pid       = FridaNative.childGetPid(h),
            parentPid = FridaNative.childGetParentPid(h),
            origin    = ChildOrigin.fromInt(FridaNative.childGetOrigin(h)),
            identifier = FridaNative.childGetIdentifier(h),
            path      = FridaNative.childGetPath(h)
        ).also { FridaNative.unref(h) }
    }
}
```

### Flow from a GObject signal

Use `callbackFlow` — this is the exact pattern used by every signal in the codebase:

```kotlin
// In FridaDevice.kt
val childAdded: Flow<FridaChild> = callbackFlow {
    val cb = FridaNative.ChildCallback { h ->
        trySend(readChild(h).also { FridaNative.unref(h) })
    }
    val cbHandle = FridaNative.deviceConnectChildAdded(handle, cb)
    awaitClose { FridaNative.deviceDisconnectChildAdded(handle, cbHandle) }
}
```

The C++ side follows the struct pattern already in `frida_jni.cpp`. Every signal struct has the same four fields:

```cpp
struct ChildSignalData {
    JavaVM*   jvm;        // for AttachCurrentThread on the GLib thread
    jobject   callback;   // global ref to the Kotlin lambda
    jmethodID methodId;   // looked up once at connect time
    gulong    handlerId;  // returned by g_signal_connect, used to disconnect
};

static void on_child_added(FridaDevice* dev, FridaChild* child, gpointer ud) {
    ChildSignalData* data = static_cast<ChildSignalData*>(ud);
    JNIEnv* env = nullptr;
    bool attached = false;
    if (data->jvm->GetEnv((void**)&env, JNI_VERSION_1_8) == JNI_EDETACHED) {
        data->jvm->AttachCurrentThread((void**)&env, nullptr);
        attached = true;
    }
    if (env) {
        g_object_ref(child);   // keep alive across JNI boundary
        env->CallVoidMethod(data->callback, data->methodId, (jlong)(intptr_t)child);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    if (attached) data->jvm->DetachCurrentThread();
}

JNIEXPORT jlong JNICALL
Java_dev_supersam_frida_FridaNative_deviceConnectChildAdded(
        JNIEnv* env, jclass cls, jlong handle, jobject cb) {
    ChildSignalData* data = new ChildSignalData();
    env->GetJavaVM(&data->jvm);
    data->callback = env->NewGlobalRef(cb);
    data->methodId = env->GetMethodID(env->GetObjectClass(cb), "onChild", "(J)V");
    data->handlerId = g_signal_connect((FridaDevice*)(intptr_t)handle,
                                        "child-added", G_CALLBACK(on_child_added), data);
    return (jlong)(intptr_t)data;
}

JNIEXPORT void JNICALL
Java_dev_supersam_frida_FridaNative_deviceDisconnectChildAdded(
        JNIEnv* env, jclass cls, jlong handle, jlong cbHandle) {
    ChildSignalData* data = (ChildSignalData*)(intptr_t)cbHandle;
    if (data) {
        g_signal_handler_disconnect((FridaDevice*)(intptr_t)handle, data->handlerId);
        env->DeleteGlobalRef(data->callback);
        delete data;
    }
}
```

---

## Implemented signals

All signals below are fully implemented and usable today:

| Kotlin property | GObject signal | On | Arg type | Notes |
|-----------------|---------------|----|----------|-------|
| `session.detached` | `"detached"` | `FridaSession` | `DetachReason` | Primitive — no `g_object_ref` needed |
| `script.messages` | `"message"` | `FridaScript` | `String` (raw JSON) | Primitive — no `g_object_ref` needed |
| `script.typedMessages` | _(Kotlin `.map`)_ | — | `ScriptMessage` | No C++ — pure Kotlin transform |
| `device.childAdded` | `"child-added"` | `FridaDevice` | `FridaChild` | Requires `enableSpawnGating()` first |
| `device.childRemoved` | `"child-removed"` | `FridaDevice` | `FridaChild` | Requires `enableSpawnGating()` first |
| `Frida.deviceAdded` | `"added"` | `FridaDeviceManager` | `FridaDevice` | — |
| `Frida.deviceRemoved` | `"removed"` | `FridaDeviceManager` | `FridaDevice` | — |
| `Frida.deviceChanged` | `"changed"` | `FridaDeviceManager` | `Unit` | No arg — `onChange()V` method |

---

## Implemented API patterns with examples

### Script message typing

`typedMessages` requires no new C++ — it's a `.map {}` on the existing `messages` Flow. `FridaMessageParser` handles the three shapes Frida always sends:

```kotlin
script.typedMessages.collect { msg ->
    when (msg) {
        is ScriptMessage.Send  -> println("payload=${msg.payload}")
        is ScriptMessage.Error -> println("ERROR: ${msg.description}\n${msg.stack}")
        is ScriptMessage.Log   -> println("[${msg.level}] ${msg.text}")
        is ScriptMessage.Raw   -> println("unknown type: ${msg.json}")
    }
}
```

To add a new `ScriptMessage` variant in a future session: add the data class to `FridaModels.kt` and a `when` branch to `FridaMessageParser.parse()`. Zero C++ changes.

### Spawn gating — the full workflow

```kotlin
// 1. Enable gating before spawning so children pause at entry
device.enableSpawnGating()

// 2. Collect children as they're created
val gatingJob = launch {
    device.childAdded.collect { child ->
        println("Child spawned: pid=${child.pid} path=${child.path}")

        // 3. Attach and instrument before it runs
        val session = device.attach(child.pid)
        val script  = session.createScript("""
            Java.perform(() => {
                console.log("Hooked early!");
            });
        """.trimIndent())
        script.load()

        // 4. Let it continue
        device.resume(child.pid)
    }
}

// 5. Spawn the target
val pid = device.spawn("/path/to/app")
device.resume(pid)   // resume the parent; children will fire childAdded

// ... later
gatingJob.cancel()
device.disableSpawnGating()
```

### Device monitoring

```kotlin
// React to USB device plug/unplug without polling
val monitorJob = launch {
    Frida.deviceAdded.collect { device ->
        println("Device connected: ${device.name} (${device.type})")
    }
}

launch {
    Frida.deviceRemoved.collect { device ->
        println("Device disconnected: ${device.name}")
    }
}

// Or just get notified something changed and re-enumerate
launch {
    Frida.deviceChanged.collect {
        val devices = Frida.enumerateDevices()
        println("Device list updated: ${devices.map { it.name }}")
    }
}
```

---

## Signals not yet implemented

| GObject signal | On | Use case |
|---------------|----|----------|
| `"destroyed"` | `FridaScript` | Script crashed or was forcibly unloaded |
| `"output"` | `FridaProcess` (via session) | stdout/stderr from spawned process |
| `"output"` | `FridaDevice` | Device-level process output |

These follow the identical struct + `callbackFlow` pattern — they just haven't been wired up yet.

---

## Planned features and where they go

### Device system parameters

```bash
grep "frida_device_query_system_parameters" frida/src/main/cpp/frida_core.h
```

Returns a `GHashTable*` of string → GVariant. Two implementation strategies:

**Option A** — serialize in C++ to JSON, parse in Kotlin (no new JNI types):
```cpp
// In the JNI function, iterate the GHashTable and build a JSON string
// Return jstring; Kotlin parses with FridaMessageParser or kotlinx.serialization
```

**Option B** — expose as `Map<String, String>` by iterating in C++ and building a Java `HashMap` via JNI (`FindClass("java/util/HashMap")`, `NewObject`, `CallObjectMethod` with `put`).

Option A is simpler to implement. Option B gives a cleaner Kotlin API.

### Remote device pairing

```bash
grep "frida_device_manager_add_remote_device" frida/src/main/cpp/frida_core.h
```

Takes a host string and optional `FridaRemoteDeviceOptions*`. Wraps as:
```kotlin
// In Frida.kt
suspend fun addRemoteDevice(address: String): FridaDevice = withContext(Dispatchers.IO) {
    FridaDevice(FridaNative.deviceManagerAddRemoteDevice(deviceManagerHandle, address))
}
```

JNI: create options with `frida_remote_device_options_new()`, pass `nullptr` for default options, call `frida_device_manager_add_remote_device_sync`.

### Interceptor / Stalker helpers

Pure Kotlin — no new C++ or JNI. Write helpers that generate the JavaScript source string:

```kotlin
object FridaInterceptor {
    fun onEnter(address: String, handler: String): String = """
        Interceptor.attach(ptr("$address"), {
            onEnter(args) { $handler }
        });
    """.trimIndent()
}

val script = session.createScript(FridaInterceptor.onEnter("0x1234", "console.log('hit')"))
script.load()
```

### Live Stalker events via typedMessages

```kotlin
// Extend ScriptMessage with a Stalker variant in FridaModels.kt:
data class Stalker(val events: List<StalkerEvent>) : ScriptMessage()

// Then in FridaMessageParser.parse():
"stalker" -> ScriptMessage.Stalker(parseStalkerEvents(json))
```

No C++ changes — everything flows through the existing `"message"` signal.

### Session reconnect on device lost

```kotlin
// In Frida.kt
suspend fun getDeviceWithRetry(id: String, retries: Int = 3): FridaDevice {
    repeat(retries) { attempt ->
        try { return getDevice(id) }
        catch (e: Exception) { delay(1_000L * (attempt + 1)) }
    }
    return getDevice(id)
}
```

Pair with `session.detached.collect` to know when to reconnect:
```kotlin
session.detached.collect { reason ->
    if (reason == DetachReason.DEVICE_LOST) {
        val newDevice  = Frida.getDeviceWithRetry(deviceId)
        val newSession = newDevice.attach(pid)
        // re-inject script ...
    }
}
```

---

## Rebuild after C++ changes

Any time you touch `frida_jni.cpp` or `CMakeLists.txt`:

```bash
./gradlew :frida:buildNative    # recompiles frida_jni.cpp (incremental)
./gradlew :frida:copyNativeLib  # copies new dylib into resources
./gradlew :app:run              # smoke test
```

---

## Common mistakes

| Mistake | Symptom | Fix |
|---------|---------|-----|
| JNI function name typo | `UnsatisfiedLinkError` at runtime | Package dots → `_`, class `FridaNative`, method camelCase. Underscores in method names → `_1` in JNI name |
| Wrong JNI method descriptor in `GetMethodID` | `null` methodId → crash on `CallVoidMethod` | Primitive `Int` → `I`, `Long` → `J`, `String` → `Ljava/lang/String;`, `void` → `V` |
| Forgot `frida_unref` on a list | Memory leak | Every `frida_*_list_size / get` pair needs `frida_unref(list)` after the loop |
| `frida_unref` on a handle still in use | Use-after-free crash | Only unref inside `detach()` / `unload()` or after all reads from the handle are done |
| Signal callback without `AttachCurrentThread` | JVM crash on the GLib thread | Always check `GetEnv` → if `JNI_EDETACHED`, call `AttachCurrentThread` before any JNI call |
| Missing `DeleteGlobalRef` in the disconnect function | JVM heap leak | Every `NewGlobalRef` in the connect function must have a matching `DeleteGlobalRef` in the disconnect |
| No `g_object_ref` on GObject signal arg | Use-after-free if the object is freed before JNI returns | Call `g_object_ref(obj)` before `CallVoidMethod`; Kotlin side calls `FridaNative.unref(h)` after reading fields |
| Collecting `childAdded` without calling `enableSpawnGating` | Signal never fires | `enableSpawnGating()` must be called on the device before children will be reported |
| `trySend` in a closed callbackFlow | `ClosedSendChannelException` (silently dropped by `trySend`) | `trySend` is safe to call even after close — it just returns `false`. Don't use `send` (suspending) from a signal callback |
