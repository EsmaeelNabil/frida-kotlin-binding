#include <jni.h>
#include "frida_core.h"
#include <cstdlib>

// Global JVM reference for cross-thread signal callbacks
static JavaVM* g_jvm = nullptr;

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_8;
}

static void throw_from_gerror(JNIEnv* env, GError* error) {
    if (!error) return;
    jclass cls = env->FindClass("java/lang/RuntimeException");
    env->ThrowNew(cls, error->message);
    g_error_free(error);
}

extern "C" {

// ============================================================
// Init / Shutdown
// ============================================================

JNIEXPORT void JNICALL
Java_dev_supersam_frida_FridaNative_fridaInit(JNIEnv* env, jclass cls) {
    frida_init();
}

JNIEXPORT void JNICALL
Java_dev_supersam_frida_FridaNative_fridaShutdown(JNIEnv* env, jclass cls) {
    frida_shutdown();
}

// ============================================================
// DeviceManager
// ============================================================

JNIEXPORT jlong JNICALL
Java_dev_supersam_frida_FridaNative_deviceManagerNew(JNIEnv* env, jclass cls) {
    return (jlong)(intptr_t)frida_device_manager_new();
}

JNIEXPORT jlongArray JNICALL
Java_dev_supersam_frida_FridaNative_deviceManagerEnumerateDevices(
        JNIEnv* env, jclass cls, jlong handle) {
    FridaDeviceManager* manager = (FridaDeviceManager*)(intptr_t)handle;
    GError* error = nullptr;
    FridaDeviceList* list = frida_device_manager_enumerate_devices_sync(manager, nullptr, &error);
    if (error) { throw_from_gerror(env, error); return nullptr; }

    gint size = frida_device_list_size(list);
    jlongArray result = env->NewLongArray(size);
    jlong* elems = env->GetLongArrayElements(result, nullptr);
    for (gint i = 0; i < size; i++) {
        elems[i] = (jlong)(intptr_t)frida_device_list_get(list, i);
    }
    env->ReleaseLongArrayElements(result, elems, 0);
    frida_unref(list);
    return result;
}

JNIEXPORT jlong JNICALL
Java_dev_supersam_frida_FridaNative_deviceManagerGetDeviceById(
        JNIEnv* env, jclass cls, jlong handle, jstring jid, jint timeout) {
    FridaDeviceManager* manager = (FridaDeviceManager*)(intptr_t)handle;
    const char* id = env->GetStringUTFChars(jid, nullptr);
    GError* error = nullptr;
    FridaDevice* device = frida_device_manager_get_device_by_id_sync(
            manager, id, (gint)timeout, nullptr, &error);
    env->ReleaseStringUTFChars(jid, id);
    if (error) { throw_from_gerror(env, error); return 0; }
    return (jlong)(intptr_t)device;
}

// ============================================================
// Device
// ============================================================

JNIEXPORT jstring JNICALL
Java_dev_supersam_frida_FridaNative_deviceGetId(JNIEnv* env, jclass cls, jlong handle) {
    return env->NewStringUTF(frida_device_get_id((FridaDevice*)(intptr_t)handle));
}

JNIEXPORT jstring JNICALL
Java_dev_supersam_frida_FridaNative_deviceGetName(JNIEnv* env, jclass cls, jlong handle) {
    return env->NewStringUTF(frida_device_get_name((FridaDevice*)(intptr_t)handle));
}

JNIEXPORT jint JNICALL
Java_dev_supersam_frida_FridaNative_deviceGetType(JNIEnv* env, jclass cls, jlong handle) {
    return (jint)frida_device_get_dtype((FridaDevice*)(intptr_t)handle);
}

JNIEXPORT jlongArray JNICALL
Java_dev_supersam_frida_FridaNative_deviceEnumerateApplications(
        JNIEnv* env, jclass cls, jlong handle, jint scope) {
    FridaDevice* device = (FridaDevice*)(intptr_t)handle;
    FridaApplicationQueryOptions* opts = frida_application_query_options_new();
    frida_application_query_options_set_scope(opts, (FridaScope)scope);
    GError* error = nullptr;
    FridaApplicationList* list = frida_device_enumerate_applications_sync(
            device, opts, nullptr, &error);
    frida_unref(opts);
    if (error) { throw_from_gerror(env, error); return nullptr; }

    gint size = frida_application_list_size(list);
    jlongArray result = env->NewLongArray(size);
    jlong* elems = env->GetLongArrayElements(result, nullptr);
    for (gint i = 0; i < size; i++) {
        elems[i] = (jlong)(intptr_t)frida_application_list_get(list, i);
    }
    env->ReleaseLongArrayElements(result, elems, 0);
    frida_unref(list);
    return result;
}

JNIEXPORT jlongArray JNICALL
Java_dev_supersam_frida_FridaNative_deviceEnumerateProcesses(
        JNIEnv* env, jclass cls, jlong handle, jint scope) {
    FridaDevice* device = (FridaDevice*)(intptr_t)handle;
    FridaProcessQueryOptions* opts = frida_process_query_options_new();
    frida_process_query_options_set_scope(opts, (FridaScope)scope);
    GError* error = nullptr;
    FridaProcessList* list = frida_device_enumerate_processes_sync(
            device, opts, nullptr, &error);
    frida_unref(opts);
    if (error) { throw_from_gerror(env, error); return nullptr; }

    gint size = frida_process_list_size(list);
    jlongArray result = env->NewLongArray(size);
    jlong* elems = env->GetLongArrayElements(result, nullptr);
    for (gint i = 0; i < size; i++) {
        elems[i] = (jlong)(intptr_t)frida_process_list_get(list, i);
    }
    env->ReleaseLongArrayElements(result, elems, 0);
    frida_unref(list);
    return result;
}

JNIEXPORT jlong JNICALL
Java_dev_supersam_frida_FridaNative_deviceAttach(
        JNIEnv* env, jclass cls, jlong handle, jint pid) {
    FridaDevice* device = (FridaDevice*)(intptr_t)handle;
    GError* error = nullptr;
    FridaSession* session = frida_device_attach_sync(
            device, (guint)pid, nullptr, nullptr, &error);
    if (error) { throw_from_gerror(env, error); return 0; }
    return (jlong)(intptr_t)session;
}

JNIEXPORT jint JNICALL
Java_dev_supersam_frida_FridaNative_deviceSpawn(
        JNIEnv* env, jclass cls, jlong handle, jstring jprogram) {
    FridaDevice* device = (FridaDevice*)(intptr_t)handle;
    const char* program = env->GetStringUTFChars(jprogram, nullptr);
    GError* error = nullptr;
    guint pid = frida_device_spawn_sync(device, program, nullptr, nullptr, &error);
    env->ReleaseStringUTFChars(jprogram, program);
    if (error) { throw_from_gerror(env, error); return 0; }
    return (jint)pid;
}

JNIEXPORT void JNICALL
Java_dev_supersam_frida_FridaNative_deviceResume(
        JNIEnv* env, jclass cls, jlong handle, jint pid) {
    FridaDevice* device = (FridaDevice*)(intptr_t)handle;
    GError* error = nullptr;
    frida_device_resume_sync(device, (guint)pid, nullptr, &error);
    if (error) throw_from_gerror(env, error);
}

// ============================================================
// Application
// ============================================================

JNIEXPORT jstring JNICALL
Java_dev_supersam_frida_FridaNative_applicationGetIdentifier(
        JNIEnv* env, jclass cls, jlong handle) {
    return env->NewStringUTF(
            frida_application_get_identifier((FridaApplication*)(intptr_t)handle));
}

JNIEXPORT jstring JNICALL
Java_dev_supersam_frida_FridaNative_applicationGetName(
        JNIEnv* env, jclass cls, jlong handle) {
    return env->NewStringUTF(
            frida_application_get_name((FridaApplication*)(intptr_t)handle));
}

JNIEXPORT jint JNICALL
Java_dev_supersam_frida_FridaNative_applicationGetPid(
        JNIEnv* env, jclass cls, jlong handle) {
    return (jint)frida_application_get_pid((FridaApplication*)(intptr_t)handle);
}

// ============================================================
// Process
// ============================================================

JNIEXPORT jint JNICALL
Java_dev_supersam_frida_FridaNative_processGetPid(
        JNIEnv* env, jclass cls, jlong handle) {
    return (jint)frida_process_get_pid((FridaProcess*)(intptr_t)handle);
}

JNIEXPORT jstring JNICALL
Java_dev_supersam_frida_FridaNative_processGetName(
        JNIEnv* env, jclass cls, jlong handle) {
    return env->NewStringUTF(frida_process_get_name((FridaProcess*)(intptr_t)handle));
}

// ============================================================
// Session
// ============================================================

JNIEXPORT jlong JNICALL
Java_dev_supersam_frida_FridaNative_sessionCreateScript(
        JNIEnv* env, jclass cls, jlong handle, jstring jsource) {
    FridaSession* session = (FridaSession*)(intptr_t)handle;
    const char* source = env->GetStringUTFChars(jsource, nullptr);
    FridaScriptOptions* opts = frida_script_options_new();
    GError* error = nullptr;
    FridaScript* script = frida_session_create_script_sync(
            session, source, opts, nullptr, &error);
    env->ReleaseStringUTFChars(jsource, source);
    frida_unref(opts);
    if (error) { throw_from_gerror(env, error); return 0; }
    return (jlong)(intptr_t)script;
}

JNIEXPORT void JNICALL
Java_dev_supersam_frida_FridaNative_sessionDetach(
        JNIEnv* env, jclass cls, jlong handle) {
    FridaSession* session = (FridaSession*)(intptr_t)handle;
    GError* error = nullptr;
    frida_session_detach_sync(session, nullptr, &error);
    if (error) throw_from_gerror(env, error);
}

// ---- detached signal ----

struct DetachedData {
    JavaVM* jvm;
    jobject callback;  // global ref
    jmethodID methodId;
    gulong handlerId;
};

static void on_session_detached(FridaSession* session, FridaSessionDetachReason reason,
                                 FridaCrash* crash, gpointer user_data) {
    DetachedData* data = static_cast<DetachedData*>(user_data);
    JNIEnv* env = nullptr;
    bool attached = false;
    if (data->jvm->GetEnv((void**)&env, JNI_VERSION_1_8) == JNI_EDETACHED) {
        data->jvm->AttachCurrentThread((void**)&env, nullptr);
        attached = true;
    }
    if (env) {
        env->CallVoidMethod(data->callback, data->methodId, (jint)reason);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    if (attached) data->jvm->DetachCurrentThread();
}

JNIEXPORT jlong JNICALL
Java_dev_supersam_frida_FridaNative_sessionConnectDetached(
        JNIEnv* env, jclass cls, jlong handle, jobject callback) {
    FridaSession* session = (FridaSession*)(intptr_t)handle;
    DetachedData* data = new DetachedData();
    env->GetJavaVM(&data->jvm);
    data->callback = env->NewGlobalRef(callback);
    jclass cbCls = env->GetObjectClass(callback);
    data->methodId = env->GetMethodID(cbCls, "onDetached", "(I)V");
    data->handlerId = g_signal_connect(session, "detached",
                                        G_CALLBACK(on_session_detached), data);
    return (jlong)(intptr_t)data;
}

JNIEXPORT void JNICALL
Java_dev_supersam_frida_FridaNative_sessionDisconnectDetached(
        JNIEnv* env, jclass cls, jlong handle, jlong cbHandle) {
    FridaSession* session = (FridaSession*)(intptr_t)handle;
    DetachedData* data = (DetachedData*)(intptr_t)cbHandle;
    if (data) {
        g_signal_handler_disconnect(session, data->handlerId);
        env->DeleteGlobalRef(data->callback);
        delete data;
    }
}

// ============================================================
// Script
// ============================================================

JNIEXPORT void JNICALL
Java_dev_supersam_frida_FridaNative_scriptLoad(
        JNIEnv* env, jclass cls, jlong handle) {
    FridaScript* script = (FridaScript*)(intptr_t)handle;
    GError* error = nullptr;
    frida_script_load_sync(script, nullptr, &error);
    if (error) throw_from_gerror(env, error);
}

JNIEXPORT void JNICALL
Java_dev_supersam_frida_FridaNative_scriptUnload(
        JNIEnv* env, jclass cls, jlong handle) {
    FridaScript* script = (FridaScript*)(intptr_t)handle;
    GError* error = nullptr;
    frida_script_unload_sync(script, nullptr, &error);
    if (error) throw_from_gerror(env, error);
}

JNIEXPORT void JNICALL
Java_dev_supersam_frida_FridaNative_scriptPost(
        JNIEnv* env, jclass cls, jlong handle, jstring jmessage) {
    FridaScript* script = (FridaScript*)(intptr_t)handle;
    const char* message = env->GetStringUTFChars(jmessage, nullptr);
    frida_script_post(script, message, nullptr);
    env->ReleaseStringUTFChars(jmessage, message);
}

// ---- message signal ----

struct MessageData {
    JavaVM* jvm;
    jobject callback;  // global ref
    jmethodID methodId;
    gulong handlerId;
};

static void on_script_message(FridaScript* script, const gchar* message,
                               GBytes* data_bytes, gpointer user_data) {
    MessageData* data = static_cast<MessageData*>(user_data);
    JNIEnv* env = nullptr;
    bool attached = false;
    if (data->jvm->GetEnv((void**)&env, JNI_VERSION_1_8) == JNI_EDETACHED) {
        data->jvm->AttachCurrentThread((void**)&env, nullptr);
        attached = true;
    }
    if (env) {
        jstring jmsg = env->NewStringUTF(message);
        env->CallVoidMethod(data->callback, data->methodId, jmsg);
        env->DeleteLocalRef(jmsg);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    if (attached) data->jvm->DetachCurrentThread();
}

JNIEXPORT jlong JNICALL
Java_dev_supersam_frida_FridaNative_scriptConnectMessage(
        JNIEnv* env, jclass cls, jlong handle, jobject callback) {
    FridaScript* script = (FridaScript*)(intptr_t)handle;
    MessageData* data = new MessageData();
    env->GetJavaVM(&data->jvm);
    data->callback = env->NewGlobalRef(callback);
    jclass cbCls = env->GetObjectClass(callback);
    data->methodId = env->GetMethodID(cbCls, "onMessage", "(Ljava/lang/String;)V");
    data->handlerId = g_signal_connect(script, "message",
                                        G_CALLBACK(on_script_message), data);
    return (jlong)(intptr_t)data;
}

JNIEXPORT void JNICALL
Java_dev_supersam_frida_FridaNative_scriptDisconnectMessage(
        JNIEnv* env, jclass cls, jlong handle, jlong cbHandle) {
    FridaScript* script = (FridaScript*)(intptr_t)handle;
    MessageData* data = (MessageData*)(intptr_t)cbHandle;
    if (data) {
        g_signal_handler_disconnect(script, data->handlerId);
        env->DeleteGlobalRef(data->callback);
        delete data;
    }
}

// ============================================================
// Memory management
// ============================================================

JNIEXPORT void JNICALL
Java_dev_supersam_frida_FridaNative_unref(JNIEnv* env, jclass cls, jlong handle) {
    if (handle != 0) frida_unref((gpointer)(intptr_t)handle);
}

// ============================================================
// Device — spawn gating & pending children
// ============================================================

JNIEXPORT void JNICALL
Java_dev_supersam_frida_FridaNative_deviceEnableSpawnGating(
        JNIEnv* env, jclass cls, jlong handle) {
    GError* error = nullptr;
    frida_device_enable_spawn_gating_sync((FridaDevice*)(intptr_t)handle, nullptr, &error);
    if (error) throw_from_gerror(env, error);
}

JNIEXPORT void JNICALL
Java_dev_supersam_frida_FridaNative_deviceDisableSpawnGating(
        JNIEnv* env, jclass cls, jlong handle) {
    GError* error = nullptr;
    frida_device_disable_spawn_gating_sync((FridaDevice*)(intptr_t)handle, nullptr, &error);
    if (error) throw_from_gerror(env, error);
}

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
        g_object_ref(child);
        elems[i] = (jlong)(intptr_t)child;
    }
    env->ReleaseLongArrayElements(result, elems, 0);
    frida_unref(list);
    return result;
}

// ---- FridaChild getters ----

JNIEXPORT jint JNICALL
Java_dev_supersam_frida_FridaNative_childGetPid(JNIEnv* env, jclass cls, jlong h) {
    return (jint)frida_child_get_pid((FridaChild*)(intptr_t)h);
}

JNIEXPORT jint JNICALL
Java_dev_supersam_frida_FridaNative_childGetParentPid(JNIEnv* env, jclass cls, jlong h) {
    return (jint)frida_child_get_parent_pid((FridaChild*)(intptr_t)h);
}

JNIEXPORT jint JNICALL
Java_dev_supersam_frida_FridaNative_childGetOrigin(JNIEnv* env, jclass cls, jlong h) {
    return (jint)frida_child_get_origin((FridaChild*)(intptr_t)h);
}

JNIEXPORT jstring JNICALL
Java_dev_supersam_frida_FridaNative_childGetIdentifier(JNIEnv* env, jclass cls, jlong h) {
    const gchar* v = frida_child_get_identifier((FridaChild*)(intptr_t)h);
    return v ? env->NewStringUTF(v) : nullptr;
}

JNIEXPORT jstring JNICALL
Java_dev_supersam_frida_FridaNative_childGetPath(JNIEnv* env, jclass cls, jlong h) {
    const gchar* v = frida_child_get_path((FridaChild*)(intptr_t)h);
    return v ? env->NewStringUTF(v) : nullptr;
}

// ---- child-added / child-removed signals (on FridaDevice) ----

struct ChildSignalData {
    JavaVM*   jvm;
    jobject   callback;   // global ref, method: onChild(J)V
    jmethodID methodId;
    gulong    handlerId;
};

static void dispatch_child(ChildSignalData* data, FridaChild* child, JNIEnv** out_env) {
    bool attached = false;
    if (data->jvm->GetEnv((void**)out_env, JNI_VERSION_1_8) == JNI_EDETACHED) {
        data->jvm->AttachCurrentThread((void**)out_env, nullptr);
        attached = true;
    }
    if (*out_env) {
        g_object_ref(child);
        (*out_env)->CallVoidMethod(data->callback, data->methodId, (jlong)(intptr_t)child);
        if ((*out_env)->ExceptionCheck()) (*out_env)->ExceptionClear();
    }
    if (attached) data->jvm->DetachCurrentThread();
}

static void on_child_added(FridaDevice* dev, FridaChild* child, gpointer ud) {
    JNIEnv* env = nullptr;
    dispatch_child(static_cast<ChildSignalData*>(ud), child, &env);
}

static void on_child_removed(FridaDevice* dev, FridaChild* child, gpointer ud) {
    JNIEnv* env = nullptr;
    dispatch_child(static_cast<ChildSignalData*>(ud), child, &env);
}

static jlong connect_child_signal(JNIEnv* env, jlong handle, jobject cb, const char* signal,
                                   GCallback fn) {
    ChildSignalData* data = new ChildSignalData();
    env->GetJavaVM(&data->jvm);
    data->callback = env->NewGlobalRef(cb);
    data->methodId = env->GetMethodID(env->GetObjectClass(cb), "onChild", "(J)V");
    data->handlerId = g_signal_connect((FridaDevice*)(intptr_t)handle, signal, fn, data);
    return (jlong)(intptr_t)data;
}

static void disconnect_child_signal(JNIEnv* env, jlong handle, jlong cbHandle, bool isDevice) {
    ChildSignalData* data = (ChildSignalData*)(intptr_t)cbHandle;
    if (data) {
        if (isDevice)
            g_signal_handler_disconnect((FridaDevice*)(intptr_t)handle, data->handlerId);
        env->DeleteGlobalRef(data->callback);
        delete data;
    }
}

JNIEXPORT jlong JNICALL
Java_dev_supersam_frida_FridaNative_deviceConnectChildAdded(
        JNIEnv* env, jclass cls, jlong handle, jobject cb) {
    return connect_child_signal(env, handle, cb, "child-added", G_CALLBACK(on_child_added));
}

JNIEXPORT void JNICALL
Java_dev_supersam_frida_FridaNative_deviceDisconnectChildAdded(
        JNIEnv* env, jclass cls, jlong handle, jlong cbHandle) {
    disconnect_child_signal(env, handle, cbHandle, true);
}

JNIEXPORT jlong JNICALL
Java_dev_supersam_frida_FridaNative_deviceConnectChildRemoved(
        JNIEnv* env, jclass cls, jlong handle, jobject cb) {
    return connect_child_signal(env, handle, cb, "child-removed", G_CALLBACK(on_child_removed));
}

JNIEXPORT void JNICALL
Java_dev_supersam_frida_FridaNative_deviceDisconnectChildRemoved(
        JNIEnv* env, jclass cls, jlong handle, jlong cbHandle) {
    disconnect_child_signal(env, handle, cbHandle, true);
}

// ============================================================
// DeviceManager — added / removed / changed signals
// ============================================================

struct DeviceSignalData {
    JavaVM*   jvm;
    jobject   callback;   // global ref, method: onDevice(J)V
    jmethodID methodId;
    gulong    handlerId;
};

static void on_device_added(FridaDeviceManager* mgr, FridaDevice* device, gpointer ud) {
    DeviceSignalData* data = static_cast<DeviceSignalData*>(ud);
    JNIEnv* env = nullptr;
    bool attached = false;
    if (data->jvm->GetEnv((void**)&env, JNI_VERSION_1_8) == JNI_EDETACHED) {
        data->jvm->AttachCurrentThread((void**)&env, nullptr);
        attached = true;
    }
    if (env) {
        g_object_ref(device);
        env->CallVoidMethod(data->callback, data->methodId, (jlong)(intptr_t)device);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    if (attached) data->jvm->DetachCurrentThread();
}

static void on_device_removed(FridaDeviceManager* mgr, FridaDevice* device, gpointer ud) {
    DeviceSignalData* data = static_cast<DeviceSignalData*>(ud);
    JNIEnv* env = nullptr;
    bool attached = false;
    if (data->jvm->GetEnv((void**)&env, JNI_VERSION_1_8) == JNI_EDETACHED) {
        data->jvm->AttachCurrentThread((void**)&env, nullptr);
        attached = true;
    }
    if (env) {
        g_object_ref(device);
        env->CallVoidMethod(data->callback, data->methodId, (jlong)(intptr_t)device);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    if (attached) data->jvm->DetachCurrentThread();
}

static jlong connect_device_signal(JNIEnv* env, jlong handle, jobject cb,
                                    const char* signal, GCallback fn) {
    DeviceSignalData* data = new DeviceSignalData();
    env->GetJavaVM(&data->jvm);
    data->callback = env->NewGlobalRef(cb);
    data->methodId = env->GetMethodID(env->GetObjectClass(cb), "onDevice", "(J)V");
    data->handlerId = g_signal_connect((FridaDeviceManager*)(intptr_t)handle, signal, fn, data);
    return (jlong)(intptr_t)data;
}

static void disconnect_device_signal(JNIEnv* env, jlong handle, jlong cbHandle) {
    DeviceSignalData* data = (DeviceSignalData*)(intptr_t)cbHandle;
    if (data) {
        g_signal_handler_disconnect((FridaDeviceManager*)(intptr_t)handle, data->handlerId);
        env->DeleteGlobalRef(data->callback);
        delete data;
    }
}

JNIEXPORT jlong JNICALL
Java_dev_supersam_frida_FridaNative_deviceManagerConnectAdded(
        JNIEnv* env, jclass cls, jlong handle, jobject cb) {
    return connect_device_signal(env, handle, cb, "added", G_CALLBACK(on_device_added));
}

JNIEXPORT void JNICALL
Java_dev_supersam_frida_FridaNative_deviceManagerDisconnectAdded(
        JNIEnv* env, jclass cls, jlong handle, jlong cbHandle) {
    disconnect_device_signal(env, handle, cbHandle);
}

JNIEXPORT jlong JNICALL
Java_dev_supersam_frida_FridaNative_deviceManagerConnectRemoved(
        JNIEnv* env, jclass cls, jlong handle, jobject cb) {
    return connect_device_signal(env, handle, cb, "removed", G_CALLBACK(on_device_removed));
}

JNIEXPORT void JNICALL
Java_dev_supersam_frida_FridaNative_deviceManagerDisconnectRemoved(
        JNIEnv* env, jclass cls, jlong handle, jlong cbHandle) {
    disconnect_device_signal(env, handle, cbHandle);
}

// ---- changed signal (no device argument) ----

struct ChangedData {
    JavaVM*   jvm;
    jobject   callback;   // global ref, method: onChange()V
    jmethodID methodId;
    gulong    handlerId;
};

static void on_device_changed(FridaDeviceManager* mgr, gpointer ud) {
    ChangedData* data = static_cast<ChangedData*>(ud);
    JNIEnv* env = nullptr;
    bool attached = false;
    if (data->jvm->GetEnv((void**)&env, JNI_VERSION_1_8) == JNI_EDETACHED) {
        data->jvm->AttachCurrentThread((void**)&env, nullptr);
        attached = true;
    }
    if (env) {
        env->CallVoidMethod(data->callback, data->methodId);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    if (attached) data->jvm->DetachCurrentThread();
}

JNIEXPORT jlong JNICALL
Java_dev_supersam_frida_FridaNative_deviceManagerConnectChanged(
        JNIEnv* env, jclass cls, jlong handle, jobject cb) {
    ChangedData* data = new ChangedData();
    env->GetJavaVM(&data->jvm);
    data->callback = env->NewGlobalRef(cb);
    data->methodId = env->GetMethodID(env->GetObjectClass(cb), "onChange", "()V");
    data->handlerId = g_signal_connect((FridaDeviceManager*)(intptr_t)handle, "changed",
                                        G_CALLBACK(on_device_changed), data);
    return (jlong)(intptr_t)data;
}

JNIEXPORT void JNICALL
Java_dev_supersam_frida_FridaNative_deviceManagerDisconnectChanged(
        JNIEnv* env, jclass cls, jlong handle, jlong cbHandle) {
    ChangedData* data = (ChangedData*)(intptr_t)cbHandle;
    if (data) {
        g_signal_handler_disconnect((FridaDeviceManager*)(intptr_t)handle, data->handlerId);
        env->DeleteGlobalRef(data->callback);
        delete data;
    }
}

// ============================================================
// Version
// ============================================================

JNIEXPORT jstring JNICALL
Java_dev_supersam_frida_FridaNative_fridaVersionString(JNIEnv* env, jclass cls) {
    return env->NewStringUTF(frida_version_string());
}

// ============================================================
// DeviceManager — remote device pairing
// ============================================================

JNIEXPORT jlong JNICALL
Java_dev_supersam_frida_FridaNative_deviceManagerAddRemoteDevice(
        JNIEnv* env, jclass cls, jlong handle, jstring jaddress) {
    FridaDeviceManager* manager = (FridaDeviceManager*)(intptr_t)handle;
    const char* address = env->GetStringUTFChars(jaddress, nullptr);
    GError* error = nullptr;
    FridaDevice* device = frida_device_manager_add_remote_device_sync(
            manager, address, nullptr, nullptr, &error);
    env->ReleaseStringUTFChars(jaddress, address);
    if (error) { throw_from_gerror(env, error); return 0; }
    return (jlong)(intptr_t)device;
}

JNIEXPORT void JNICALL
Java_dev_supersam_frida_FridaNative_deviceManagerRemoveRemoteDevice(
        JNIEnv* env, jclass cls, jlong handle, jstring jaddress) {
    FridaDeviceManager* manager = (FridaDeviceManager*)(intptr_t)handle;
    const char* address = env->GetStringUTFChars(jaddress, nullptr);
    GError* error = nullptr;
    frida_device_manager_remove_remote_device_sync(manager, address, nullptr, &error);
    env->ReleaseStringUTFChars(jaddress, address);
    if (error) throw_from_gerror(env, error);
}

// ============================================================
// Script — destroyed signal
// ============================================================

struct DestroyedData {
    JavaVM*   jvm;
    jobject   callback;   // global ref, method: onDestroyed()V
    jmethodID methodId;
    gulong    handlerId;
};

static void on_script_destroyed(FridaScript* script, gpointer user_data) {
    DestroyedData* data = static_cast<DestroyedData*>(user_data);
    JNIEnv* env = nullptr;
    bool attached = false;
    if (data->jvm->GetEnv((void**)&env, JNI_VERSION_1_8) == JNI_EDETACHED) {
        data->jvm->AttachCurrentThread((void**)&env, nullptr);
        attached = true;
    }
    if (env) {
        env->CallVoidMethod(data->callback, data->methodId);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    if (attached) data->jvm->DetachCurrentThread();
}

JNIEXPORT jlong JNICALL
Java_dev_supersam_frida_FridaNative_scriptConnectDestroyed(
        JNIEnv* env, jclass cls, jlong handle, jobject callback) {
    FridaScript* script = (FridaScript*)(intptr_t)handle;
    DestroyedData* data = new DestroyedData();
    env->GetJavaVM(&data->jvm);
    data->callback = env->NewGlobalRef(callback);
    data->methodId = env->GetMethodID(env->GetObjectClass(callback), "onDestroyed", "()V");
    data->handlerId = g_signal_connect(script, "destroyed",
                                        G_CALLBACK(on_script_destroyed), data);
    return (jlong)(intptr_t)data;
}

JNIEXPORT void JNICALL
Java_dev_supersam_frida_FridaNative_scriptDisconnectDestroyed(
        JNIEnv* env, jclass cls, jlong handle, jlong cbHandle) {
    FridaScript* script = (FridaScript*)(intptr_t)handle;
    DestroyedData* data = (DestroyedData*)(intptr_t)cbHandle;
    if (data) {
        g_signal_handler_disconnect(script, data->handlerId);
        env->DeleteGlobalRef(data->callback);
        delete data;
    }
}

// ============================================================
// Session — script snapshots
// ============================================================

JNIEXPORT jbyteArray JNICALL
Java_dev_supersam_frida_FridaNative_sessionSnapshotScript(
        JNIEnv* env, jclass cls, jlong handle, jstring jembedScript, jstring jwarmupScript) {
    FridaSession* session = (FridaSession*)(intptr_t)handle;
    const char* embedScript = env->GetStringUTFChars(jembedScript, nullptr);
    FridaSnapshotOptions* opts = frida_snapshot_options_new();
    if (jwarmupScript) {
        const char* warmup = env->GetStringUTFChars(jwarmupScript, nullptr);
        if (*warmup != '\0') frida_snapshot_options_set_warmup_script(opts, warmup);
        env->ReleaseStringUTFChars(jwarmupScript, warmup);
    }
    GError* error = nullptr;
    GBytes* bytes = frida_session_snapshot_script_sync(session, embedScript, opts, nullptr, &error);
    env->ReleaseStringUTFChars(jembedScript, embedScript);
    frida_unref(opts);
    if (error) { throw_from_gerror(env, error); return nullptr; }
    gsize size;
    const guint8* raw = (const guint8*)g_bytes_get_data(bytes, &size);
    jbyteArray result = env->NewByteArray((jsize)size);
    env->SetByteArrayRegion(result, 0, (jsize)size, (const jbyte*)raw);
    g_bytes_unref(bytes);
    return result;
}

JNIEXPORT jlong JNICALL
Java_dev_supersam_frida_FridaNative_sessionCreateScriptFromSnapshot(
        JNIEnv* env, jclass cls, jlong handle, jstring jsource, jbyteArray jsnapshot) {
    FridaSession* session = (FridaSession*)(intptr_t)handle;
    const char* source = env->GetStringUTFChars(jsource, nullptr);
    jsize len = env->GetArrayLength(jsnapshot);
    jbyte* buf = env->GetByteArrayElements(jsnapshot, nullptr);
    GBytes* snapshot = g_bytes_new(buf, (gsize)len);
    env->ReleaseByteArrayElements(jsnapshot, buf, JNI_ABORT);
    FridaScriptOptions* opts = frida_script_options_new();
    frida_script_options_set_snapshot(opts, snapshot);
    g_bytes_unref(snapshot);
    GError* error = nullptr;
    FridaScript* script = frida_session_create_script_sync(
            session, source, opts, nullptr, &error);
    env->ReleaseStringUTFChars(jsource, source);
    frida_unref(opts);
    if (error) { throw_from_gerror(env, error); return 0; }
    return (jlong)(intptr_t)script;
}

} // extern "C"
