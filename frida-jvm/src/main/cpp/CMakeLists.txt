cmake_minimum_required(VERSION 3.10)
project(libfrida_wrapper)

set(CMAKE_CXX_STANDARD 11)

# Find Java
find_package(JNI REQUIRED)

include_directories(
    ${JNI_INCLUDE_DIRS}
    ${JAVA_HOME}/include
    ${JAVA_HOME}/include/darwin
)

# Find GLib/GObject/GIO via pkg-config
find_package(PkgConfig REQUIRED)
pkg_check_modules(GLIB REQUIRED glib-2.0 gobject-2.0 gio-2.0)

include_directories(
    ${GLIB_INCLUDE_DIRS}
    ${CMAKE_CURRENT_SOURCE_DIR}
)

link_directories(${GLIB_LIBRARY_DIRS})

# Hand-written JNI bridge (replaces SWIG-generated wrapper)
add_library(frida_wrapper SHARED
    frida_jni.cpp
)

target_link_libraries(frida_wrapper
    ${CMAKE_CURRENT_SOURCE_DIR}/libfrida-core.a
    ${JNI_LIBRARIES}
    ${GLIB_LIBRARIES}
)

if(APPLE)
    target_link_libraries(frida_wrapper
        "-framework Foundation"
        "-framework AppKit"
        "-framework IOKit"
        "-framework Security"
        "-lresolv"
    )
    set_target_properties(frida_wrapper PROPERTIES
        PREFIX "lib"
        SUFFIX ".dylib"
    )
else()
    set_target_properties(frida_wrapper PROPERTIES
        PREFIX "lib"
        SUFFIX ".so"
    )
endif()

target_compile_definitions(frida_wrapper PRIVATE
    ${GLIB_CFLAGS_OTHER}
    GLIB_DISABLE_DEPRECATION_WARNINGS
    _REENTRANT
)
