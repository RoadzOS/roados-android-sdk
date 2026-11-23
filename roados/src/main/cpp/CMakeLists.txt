# Native part of the RoadOS Android SDK: the JNI bridge (libroados.so) linked
# against RoadOS Graphics Core (pinned submodule) and Skia.
#
# Built by Gradle (externalNativeBuild) for each ABI. It can also be built for
# the host JVM (macOS/Linux) by scripts/host_jni_test.sh, which runs the bridge
# against the real core without an Android device.
cmake_minimum_required(VERSION 3.24)
project(roados_android LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(ROADOS_CORE_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../external/roados-graphics-core"
    CACHE PATH "RoadOS Graphics Core checkout")
set(ROADOS_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(ROADOS_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(ROADOS_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
# ROADOS_SKIA_DIR is passed by Gradle; FindSkia picks out/Release-android-<cpu>
# (or the host build) automatically.
add_subdirectory("${ROADOS_CORE_DIR}" roados-core)

add_library(roados SHARED roados_jni.cpp)
target_link_libraries(roados PRIVATE RoadOS::binding)
set_target_properties(roados PROPERTIES CXX_VISIBILITY_PRESET hidden VISIBILITY_INLINES_HIDDEN ON)

if(ANDROID)
  target_link_libraries(roados PRIVATE log)
  target_link_options(roados PRIVATE
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL           # keep Skia/core symbols private; only JNI entry points are exported
    -Wl,-z,max-page-size=16384)      # 16 KB page-size devices (Android 15+)
else()
  # Host build: only the JNI headers are needed (no libjvm link; JNI calls go
  # through JNIEnv). Use ROADOS_JNI_INCLUDE_DIRS when the JDK ships no headers.
  if(ROADOS_JNI_INCLUDE_DIRS)
    target_include_directories(roados PRIVATE ${ROADOS_JNI_INCLUDE_DIRS})
  else()
    find_package(JNI REQUIRED)
    target_include_directories(roados PRIVATE ${JNI_INCLUDE_DIRS})
  endif()
endif()
