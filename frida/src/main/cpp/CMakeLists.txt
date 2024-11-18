cmake_minimum_required(VERSION 3.10)
project(libfrida_wrapper)

# Set C++ standard
set(CMAKE_CXX_STANDARD 11)

# Find Java
find_package(JNI REQUIRED)

# Manually set JNI include paths if necessary
include_directories(
    ${JNI_INCLUDE_DIRS}
    ${JAVA_HOME}/include
    ${JAVA_HOME}/include/darwin
)

# Find GLib using Homebrew paths
find_package(PkgConfig REQUIRED)
pkg_check_modules(GLIB REQUIRED glib-2.0 gobject-2.0 gio-2.0)

# Option to use preprocessed header
option(USE_PREPROCESSED_HEADERS "Use preprocessed frida-core.h" ON)

if(USE_PREPROCESSED_HEADERS)
    set(FRIDA_HEADER "preprocessed_frida_core.h")
else()
    set(FRIDA_HEADER "frida-core.h")
endif()

# Include directories
include_directories(
    ${GLIB_INCLUDE_DIRS}
    /opt/homebrew/include/glib-2.0
    /opt/homebrew/lib/glib-2.0/include
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/generated
)

# Add library search paths
link_directories(${GLIB_LIBRARY_DIRS} /opt/homebrew/lib)

# Create the shared library from the SWIG-generated wrapper
add_library(frida_wrapper SHARED
    generated/frida_wrap.cpp
)

# Link libraries
target_link_libraries(frida_wrapper
    ${CMAKE_CURRENT_SOURCE_DIR}/libfrida-core.a
    ${JNI_LIBRARIES}
    ${GLIB_LIBRARIES}
)

# Add platform-specific libraries for macOS
if(APPLE)
    target_link_libraries(frida_wrapper
        "-framework Foundation"
        "-framework AppKit"
        "-framework IOKit"
        "-framework Security"
        "-lresolv"
    )
endif()

# Set output name
if(APPLE)
    set_target_properties(frida_wrapper
        PROPERTIES
        PREFIX "lib"
        SUFFIX ".dylib"
    )
else()
    set_target_properties(frida_wrapper
        PROPERTIES
        PREFIX "lib"
        SUFFIX ".so"
    )
endif()

# Pass additional flags for GLib and compatibility
add_definitions(
    ${GLIB_CFLAGS_OTHER}
    -DGLIB_DISABLE_DEPRECATION_WARNINGS
    -D_REENTRANT
    -D_POSIX_C_SOURCE=200809L
    -DFRIDA_HEADER=${FRIDA_HEADER}
)
