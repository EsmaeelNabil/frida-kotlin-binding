/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (https://www.swig.org).
 * Version 4.3.0
 *
 * Do not make changes to this file unless you know what you are doing - modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package dev.supersam.fridaSource;

public class fridaJNI {
  public final static native void frida_init();
  public final static native void frida_shutdown();
  public final static native long frida_device_manager_new();
  public final static native long frida_device_manager_get_device_by_id_sync(long jarg1, String jarg2, int jarg3);
  public final static native long frida_device_manager_enumerate_devices_sync(long jarg1);
  public final static native int frida_device_list_size(long jarg1);
  public final static native long frida_device_list_get(long jarg1, int jarg2);
  public final static native String frida_device_get_id(long jarg1);
  public final static native String frida_device_get_name(long jarg1);
  public final static native int frida_device_get_dtype(long jarg1);
  public final static native void frida_unref(long jarg1);
  public final static native long frida_device_enumerate_applications_sync(long jarg1, long jarg2);
  public final static native int frida_application_list_size(long jarg1);
  public final static native long frida_application_list_get(long jarg1, int jarg2);
  public final static native String frida_application_get_identifier(long jarg1);
  public final static native String frida_application_get_name(long jarg1);
  public final static native long frida_application_get_pid(long jarg1);
  public final static native long frida_application_get_parameters(long jarg1);
  public final static native long frida_application_query_options_new();
  public final static native int frida_application_query_options_get_scope(long jarg1);
  public final static native void frida_application_query_options_set_scope(long jarg1, int jarg2);
  public final static native void frida_application_query_options_select_identifier(long jarg1, String jarg2);
  public final static native long frida_application_query_options_has_selected_identifiers(long jarg1);
  public final static native void frida_application_query_options_enumerate_selected_identifiers(long jarg1, long jarg2, long jarg3);
  public final static native long frida_device_enumerate_processes_sync(long jarg1, long jarg2);
  public final static native int frida_process_list_size(long jarg1);
  public final static native long frida_process_list_get(long jarg1, int jarg2);
  public final static native long frida_process_get_pid(long jarg1);
  public final static native String frida_process_get_name(long jarg1);
}
