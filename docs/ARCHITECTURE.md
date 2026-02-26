# Architecture

## Layer diagram

```
┌─────────────────────────────────────────────────────────┐
│  Kotlin application (app/ or any consumer)              │
│                                                         │
│  runBlocking { Frida.enumerateDevices() }               │
│  script.typedMessages.collect { msg -> ... }            │
│  device.childAdded.collect { child -> ... }             │
│  Frida.deviceAdded.collect { d -> ... }                 │
└──────────────────────┬──────────────────────────────────┘
                       │ suspend fns / Flow<T>
┌──────────────────────▼──────────────────────────────────┐
│  Kotlin API layer   (frida/src/main/kotlin/)            │
│                                                         │
│  Frida.kt            top-level object, device manager   │
│                      version: String                    │
│                      addRemoteDevice / removeRemoteDevice│
│                      deviceAdded / deviceRemoved /      │
│                      deviceChanged: Flow                 │
│  FridaDevice.kt      attach / spawn / enumerate         │
│                      enableSpawnGating                  │
│                      childAdded / childRemoved: Flow     │
│  FridaSession.kt     createScript / detach              │
│                      snapshotScript / createScriptFromSnapshot│
│                      detached: Flow<DetachReason>        │
│  FridaScript.kt      load / unload / post               │
│                      messages: Flow<String>             │
│                      typedMessages: Flow<ScriptMessage> │
│                      destroyed: Flow<Unit>              │
│  FridaInterceptor.kt Interceptor + Stalker wrappers     │
│                      (extension props on FridaSession)  │
│  FridaReconnect.kt   attachWithReconnect extension      │
│  FridaMessageParser  JSON → ScriptMessage (no deps)     │
│  FridaModels.kt      data classes and enums             │
│  NativeLoader.kt     loads libfrida_wrapper at startup  │
└──────────────────────┬──────────────────────────────────┘
                       │ @JvmStatic external fns (jlong handles)
┌──────────────────────▼──────────────────────────────────┐
│  FridaNative.kt     (internal object)                   │
│                                                         │
│  All native method declarations live here.              │
│  Pointers are passed as jlong (intptr_t in C++).        │
│  Callback interfaces for GObject signals:               │
│    DetachedCallback   onDetached(Int)                   │
│    MessageCallback    onMessage(String)                 │
│    ChildCallback      onChild(Long)                     │
│    DeviceCallback     onDevice(Long)                    │
│    ChangedCallback    onChange()                        │
│    DestroyedCallback  onDestroyed()                     │
└──────────────────────┬──────────────────────────────────┘
                       │ JNI
┌──────────────────────▼──────────────────────────────────┐
│  frida_jni.cpp      (libfrida_wrapper.dylib / .so)      │
│                                                         │
│  Hand-written JNI functions, one per FridaNative method │
│  Signal structs: DetachedData, MessageData,             │
│    ChildSignalData, DeviceSignalData, ChangedData,      │
│    DestroyedData                                        │
│  JNI_OnLoad stores JavaVM* for cross-thread callbacks   │
└──────────────────────┬──────────────────────────────────┘
                       │ C API
┌──────────────────────▼──────────────────────────────────┐
│  libfrida-core.a + frida_core.h   (Frida 17.7.3)        │
│                                                         │
│  frida_init / frida_shutdown                            │
│  frida_device_manager_*                                 │
│  frida_device_*  (including spawn gating)               │
│  frida_child_*                                          │
│  frida_session_*                                        │
│  frida_script_*                                         │
│  GLib / GObject / GIO (bundled inside libfrida-core.a)  │
└─────────────────────────────────────────────────────────┘
```

---

## Key design decisions

### jlong as opaque handle

Every Frida C object (`FridaDevice*`, `FridaSession*`, `FridaScript*`, `FridaChild*`, …) is passed between Kotlin and C as a `jlong`. In C++ this is cast with `(intptr_t)`:

```cpp
// C → Kotlin
return (jlong)(intptr_t)frida_device_manager_new();

// Kotlin → C
FridaDevice* device = (FridaDevice*)(intptr_t)handle;
```

This eliminates all the SWIG-era `SWIGTYPE_p_*` Java wrapper classes. There are no heap-allocated JVM wrapper objects — the only allocation per signal connection is a small C++ struct on the heap.

### Blocking calls on Dispatchers.IO

Every `frida_*_sync` C function blocks the calling thread. All of them are wrapped in `withContext(Dispatchers.IO)` so they never block the main or a coroutine dispatcher thread:

```kotlin
suspend fun enumerateApplications(...): List<Application> =
    withContext(Dispatchers.IO) {
        // frida_device_enumerate_applications_sync called here
    }
```

### GObject signals → Flow via callbackFlow

Frida emits GObject signals from its internal GLib main loop thread. These are bridged to Kotlin `Flow<T>` using `callbackFlow`. The same pattern applies to every signal in the codebase:

```
GLib main loop thread
        │
        │  g_signal fires (e.g. "message" on FridaScript)
        │
        ▼
frida_jni.cpp:  AttachCurrentThread → CallVoidMethod(callback, methodId, args)
        │
        ▼
FridaNative.MessageCallback.onMessage(json: String)     [Kotlin lambda]
        │
        ▼
callbackFlow { trySend(json) }
        │
        ▼
script.messages.collect { json -> ... }                  [consumer coroutine]
```

Then `typedMessages` sits on top as a pure Kotlin transform:

```
script.messages          Flow<String>   — raw JSON from C
    .map { FridaMessageParser.parse(it) }
script.typedMessages     Flow<ScriptMessage>  — parsed, no C++ involved
```

Every signal connection is represented in C++ by a typed struct:

```cpp
// All signal structs share the same four fields
struct MessageData {
    JavaVM*   jvm;        // for AttachCurrentThread on the GLib thread
    jobject   callback;   // global ref to the Kotlin lambda
    jmethodID methodId;   // pre-looked-up once at connect time
    gulong    handlerId;  // for g_signal_handler_disconnect at close
};
```

`callbackFlow`'s `awaitClose` block disconnects the signal and frees the struct when the Flow collector scope ends or is cancelled.

### g_object_ref for signal arguments that are GObjects

When a signal callback receives a GObject pointer (e.g. `FridaChild*` in `child-added`, `FridaDevice*` in `added`/`removed`), the pointer is only guaranteed valid for the duration of the callback. Before passing the handle to the JVM, call `g_object_ref` to increment the reference count:

```cpp
static void on_child_added(FridaDevice* dev, FridaChild* child, gpointer ud) {
    // ...
    g_object_ref(child);   // keep alive across the JNI call boundary
    env->CallVoidMethod(data->callback, data->methodId, (jlong)(intptr_t)child);
}
```

The Kotlin side then reads any needed fields from the handle and calls `FridaNative.unref(h)` immediately — typically via `.also { FridaNative.unref(h) }` after the data class is constructed. Signals that pass primitives (e.g. `FridaSessionDetachReason` as `jint`) don't need this.

### FridaMessageParser — zero-dependency JSON extraction

`FridaScript.typedMessages` is a pure Kotlin `Flow<ScriptMessage>` derived from `messages` via `.map { FridaMessageParser.parse(it) }`. The parser handles exactly the three message shapes Frida always produces:

| `type` | Maps to |
|--------|---------|
| `"send"` | `ScriptMessage.Send(payload: String)` — payload is the raw JSON value |
| `"error"` | `ScriptMessage.Error(description, stack, fileName, lineNumber)` |
| `"log"` | `ScriptMessage.Log(level, text)` |
| anything else | `ScriptMessage.Raw(json)` — forward-compatible fallback |

The parser uses bracket-depth tracking to correctly extract nested JSON objects/arrays as the `payload` value without any external library. No C++ changes were needed — the raw string was already flowing through.

### Memory management

Frida uses GObject reference counting. The rules applied throughout the codebase:

| Object | Ownership rule |
|--------|---------------|
| Lists (`FridaDeviceList`, `FridaApplicationList`, etc.) | Caller owns — `frida_unref(list)` after iterating |
| Items from `frida_*_list_get` | Share list ref — read fields, then `frida_unref(item)` |
| Signal GObject args (`FridaChild*`, `FridaDevice*`) | Borrowed — `g_object_ref` before crossing JNI, `frida_unref` after use |
| `FridaSession` / `FridaScript` | Caller owns — `frida_unref` inside `detach()` / `unload()` |
| `FridaDeviceManager` / `FridaDevice` (in `Frida` object) | Singleton lifetime — never unref'd |

All ref/unref calls are hidden inside the Kotlin API; consumers never touch raw handles.

### Native library loading

`NativeLoader.load()` (called lazily from `Frida.deviceManagerHandle`) tries two strategies in order:

1. `System.loadLibrary("frida_wrapper")` — works when `java.library.path` points at the CMake build dir. The `app:run` Gradle task sets this via `systemProperty`.
2. Classpath extraction — extracts `libfrida_wrapper.dylib` / `.so` from the JAR's `/native/<os>/` resource path to a temp file, then loads it. Used when the library is bundled in a fat JAR for distribution.

---

## Thread model

```
JVM main thread          Dispatchers.IO pool         GLib main loop thread
       │                        │                             │
   runBlocking                  │                             │
       │── suspend fn ──────►  frida_*_sync()                │
       │◄──────────────── return value                        │
       │                        │                             │
       │── collect(flow) ───────┼─────────────────────────── signal fires
       │◄─────────────────── trySend() ◄──── CallVoidMethod() │
```

Frida's internal GLib main loop starts when `frida_init()` is called and runs in a thread managed by Frida — you never need to create or pump a `GMainLoop` manually. All signal callbacks arrive on that thread and use `AttachCurrentThread` before calling into the JVM.

---

## Implemented signals

| Kotlin property | GObject signal | Object | Carries |
|-----------------|---------------|--------|---------|
| `session.detached` | `"detached"` | `FridaSession` | `DetachReason` |
| `script.messages` | `"message"` | `FridaScript` | raw JSON `String` |
| `script.typedMessages` | _(Kotlin map of messages)_ | — | `ScriptMessage` |
| `script.destroyed` | `"destroyed"` | `FridaScript` | `Unit` |
| `device.childAdded` | `"child-added"` | `FridaDevice` | `FridaChild` |
| `device.childRemoved` | `"child-removed"` | `FridaDevice` | `FridaChild` |
| `Frida.deviceAdded` | `"added"` | `FridaDeviceManager` | `FridaDevice` |
| `Frida.deviceRemoved` | `"removed"` | `FridaDeviceManager` | `FridaDevice` |
| `Frida.deviceChanged` | `"changed"` | `FridaDeviceManager` | `Unit` |

---

## Script snapshots

Frida supports V8 heap snapshots to reduce cold-start overhead for scripts that load large libraries or require expensive initialization. The workflow is:

1. **Snapshot creation** — `session.snapshotScript(embedScript, warmupScript?)` calls `frida_session_snapshot_script_sync`, which runs `embedScript` in an isolated V8 context, optionally runs `warmupScript` to warm the JIT, and returns the heap as a `ByteArray` wrapped in `ScriptSnapshot`.

2. **Snapshot consumption** — `session.createScriptFromSnapshot(snapshot, source)` calls `frida_session_create_script_sync` with `FridaScriptOptions::set_snapshot`. The resulting `FridaScript` starts from the pre-initialized heap state rather than an empty context.

No new GObject signals are involved — the snapshot bytes cross JNI as `jbyteArray` ↔ `ByteArray`.

```
session.snapshotScript("require('frida-java-bridge');")
        │  frida_session_snapshot_script_sync
        │  → GBytes* → jbyteArray → ByteArray
        ▼
ScriptSnapshot(bytes)

session.createScriptFromSnapshot(snapshot, "Java.perform(() => { ... });")
        │  frida_session_create_script_sync with opts.snapshot = GBytes
        ▼
FridaScript — starts with Java bridge pre-loaded
```

---

## Session reconnect — `attachWithReconnect`

`attachWithReconnect` is a pure Kotlin extension on `FridaDevice` in `FridaReconnect.kt`. No JNI changes were required. The design:

```
attachWithReconnect(pid) { session ->
    [block runs in coroutineScope]
        ┌─ launch { session.detached.first() }   ← watcher
        │       DEVICE_LOST / CONNECTION_TERMINATED?
        │           set shouldRetry = true
        │           outerScope.cancel()  ← cancels block + watcher
        └─ block(session)                 ← user code
}
    CancellationException caught; shouldRetry == true → delay → re-attach
```

The watcher and the block run as siblings inside the same `coroutineScope`. When the watcher cancels the scope, any child coroutines launched by the block (message collectors, etc.) are torn down automatically before the retry.

---

## Interceptor and Stalker helpers

`FridaInterceptor.kt` provides `Interceptor` and `Stalker` as pure Kotlin classes that inject JavaScript snippets via `session.createScript()`. No new C++ or JNI is involved — these sit entirely on top of the existing `FridaSession.createScript` + `FridaScript.load` path.

```
session.interceptor.attach("0x10001234", onEnter = "send(args[0]);")
    → session.createScript("""
          Interceptor.attach(ptr('0x10001234'), {
            onEnter: function(args) { send(args[0]); },
          });
      """)
    → script.load()
    → returns FridaScript (keep to collect typedMessages / unload)
```

`session.interceptor` and `session.stalker` are extension properties that create new `Interceptor`/`Stalker` instances bound to the session. Since they hold no state themselves, creating them per-call is fine.

---

## Module boundaries

| What lives where | Reason |
|-----------------|--------|
| `FridaNative` is `internal` | Consumers should never call raw JNI handles directly |
| `FridaMessageParser` is `internal` | Parser is an implementation detail of `FridaScript` |
| `FridaModels` has no Frida dependency | Can be used in shared/multiplatform modules |
| `NativeLoader` is `internal` | Loading strategy is an implementation detail |
| `frida-gadget` is a separate module | Android-only; keeps the JVM controller pure JVM |
