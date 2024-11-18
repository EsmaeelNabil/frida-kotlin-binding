%module frida

%{
#include "frida_core.h"
%}

// Map GLib Primitive Types to Java Types
typedef unsigned int guint;
typedef int gint;
typedef char gchar;
typedef void* gpointer;

// GError Handling
typedef struct _GError GError;

%typemap(in, numinputs=0) GError **error (GError *temp) {
    temp = NULL;
    $1 = &temp;
}

%typemap(freearg) GError **error {
    if (*$1 != NULL) {
        g_error_free(*$1);
    }
}

// GCancellable Handling
typedef struct _GCancellable GCancellable;

%typemap(in, numinputs=0) GCancellable *cancellable {
    $1 = NULL;
}

// Frida Initialization Functions
extern void frida_init(void);
extern void frida_shutdown(void);

// Device Manager
%rename(DeviceManager) FridaDeviceManager;
typedef struct _FridaDeviceManager FridaDeviceManager;

extern FridaDeviceManager* frida_device_manager_new(void);
%newobject frida_device_manager_new;

extern FridaDevice* frida_device_manager_get_device_by_id_sync(
    FridaDeviceManager* self,
    const gchar* id,
    gint timeout,
    GCancellable* cancellable,
    GError** error
);

// Device List
typedef struct _FridaDeviceList FridaDeviceList;

extern FridaDeviceList* frida_device_manager_enumerate_devices_sync(
    FridaDeviceManager* self,
    GCancellable* cancellable,
    GError** error
);

extern gint frida_device_list_size(FridaDeviceList* self);
extern FridaDevice* frida_device_list_get(FridaDeviceList* self, gint index);

// Device Information
typedef struct _FridaDevice FridaDevice;
typedef enum {
  FRIDA_DEVICE_TYPE_LOCAL,
  FRIDA_DEVICE_TYPE_REMOTE,
  FRIDA_DEVICE_TYPE_USB
} FridaDeviceType;

extern const gchar* frida_device_get_id(FridaDevice* self);
extern const gchar* frida_device_get_name(FridaDevice* self);
extern FridaDeviceType frida_device_get_dtype(FridaDevice* self);

// Memory Management
extern void frida_unref(gpointer obj);
%delobject frida_unref;

// Application Enumeration
typedef struct _FridaApplicationList FridaApplicationList;
typedef struct _FridaApplicationQueryOptions FridaApplicationQueryOptions;
typedef struct _FridaApplication FridaApplication;

extern FridaApplicationList* frida_device_enumerate_applications_sync(
    FridaDevice* self,
    FridaApplicationQueryOptions* options,
    GCancellable* cancellable,
    GError** error
);

extern gint frida_application_list_size(FridaApplicationList* self);
extern FridaApplication* frida_application_list_get(FridaApplicationList* self, gint index);

extern const gchar* frida_application_get_identifier(FridaApplication* self);
extern const gchar* frida_application_get_name(FridaApplication* self);
extern guint frida_application_get_pid (FridaApplication * self);
extern GHashTable * frida_application_get_parameters (FridaApplication * self);

// Application Query Options
typedef enum {
    FRIDA_SCOPE_MINIMAL,
    FRIDA_SCOPE_METADATA,
    FRIDA_SCOPE_FULL
} FridaScope;

extern FridaApplicationQueryOptions* frida_application_query_options_new(void);
%newobject frida_application_query_options_new;

extern FridaScope frida_application_query_options_get_scope(FridaApplicationQueryOptions* self);
extern void frida_application_query_options_set_scope(FridaApplicationQueryOptions* self, FridaScope value);

extern void frida_application_query_options_select_identifier(
    FridaApplicationQueryOptions* self,
    const gchar* identifier
);

extern gboolean frida_application_query_options_has_selected_identifiers(FridaApplicationQueryOptions* self);

extern void frida_application_query_options_enumerate_selected_identifiers(
    FridaApplicationQueryOptions* self,
    GFunc func,
    gpointer user_data
);

// Process Enumeration
typedef struct _FridaProcessList FridaProcessList;
typedef struct _FridaProcess FridaProcess;

extern FridaProcessList * frida_device_enumerate_processes_sync (
    FridaDevice * self,
    FridaProcessQueryOptions* options,
    GCancellable*
    cancellable,
    GError** error
);

extern gint frida_process_list_size(FridaProcessList* self);
extern FridaProcess* frida_process_list_get(FridaProcessList* self, gint index);

extern guint frida_process_get_pid(FridaProcess* self);
extern const gchar* frida_process_get_name(FridaProcess* self);

// Frida Utilities
%newobject frida_device_manager_enumerate_devices_sync;
%newobject frida_device_list_get;
%newobject frida_device_enumerate_applications_sync;
%newobject frida_application_query_options_new;
%newobject frida_device_enumerate_processes_sync;
