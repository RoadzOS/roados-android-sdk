#!/usr/bin/env bash
# Builds the Android SDK's JNI bridge (roados/src/main/cpp) for the HOST
# (macOS or Linux) and runs host-test/HostBridgeTest on a desktop JVM.
#
# This exercises the real JNI symbols, marshalling and error mapping against the
# real RoadOS Graphics Core and Skia, with no Android device or emulator. It
# does not cover Kotlin, Bitmap, Compose or View code; run the instrumentation
# tests (./gradlew connectedCheck) for those.
#
# Environment:
#   JAVA_HOME                JDK with javac (default: Android Studio's bundled JBR on macOS)
#   ROADOS_JNI_INCLUDE_DIRS  directories with jni.h and jni_md.h, if $JAVA_HOME/include is absent
#   ROADOS_SKIA_DIR          Skia checkout with a host build (default: <core>/third_party/skia;
#                            built automatically if missing)
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CORE="$ROOT/external/roados-graphics-core"
BUILD="$ROOT/build/host-jni"
export ROADOS_SKIA_DIR="${ROADOS_SKIA_DIR:-$CORE/third_party/skia}"

if [[ -z "${JAVA_HOME:-}" && -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi
[[ -x "${JAVA_HOME:-}/bin/javac" ]] || { echo "Set JAVA_HOME to a JDK (javac required)" >&2; exit 1; }

JNI_ARGS=()
if [[ -n "${ROADOS_JNI_INCLUDE_DIRS:-}" ]]; then
  JNI_ARGS=(-DROADOS_JNI_INCLUDE_DIRS="$ROADOS_JNI_INCLUDE_DIRS")
elif [[ -f "$JAVA_HOME/include/jni.h" ]]; then
  MD="$(dirname "$(find "$JAVA_HOME/include" -name jni_md.h | head -1)")"
  JNI_ARGS=(-DROADOS_JNI_INCLUDE_DIRS="$JAVA_HOME/include;$MD")
else
  echo "No jni.h in $JAVA_HOME/include; set ROADOS_JNI_INCLUDE_DIRS" >&2
  exit 1
fi

HOST_OUT="$ROADOS_SKIA_DIR/out/Release-$( [[ "$(uname -s)" == Darwin ]] && echo mac || echo linux )-$( [[ "$(uname -m)" == x86_64 ]] && echo x64 || echo arm64 )"
[[ -f "$HOST_OUT/libskia.a" ]] || "$CORE/scripts/bootstrap_skia.sh" Release host

cmake -S "$ROOT/roados/src/main/cpp" -B "$BUILD/native" -DCMAKE_BUILD_TYPE=Release \
  -DROADOS_SKIA_DIR="$ROADOS_SKIA_DIR" "${JNI_ARGS[@]}" >/dev/null
cmake --build "$BUILD/native" --target roados -j"$(getconf _NPROCESSORS_ONLN)" >/dev/null

rm -rf "$BUILD/classes"
"$JAVA_HOME/bin/javac" -d "$BUILD/classes" "$ROOT"/host-test/src/com/roadzos/graphics/*.java
"$JAVA_HOME/bin/java" --enable-native-access=ALL-UNNAMED -Djava.library.path="$BUILD/native" -cp "$BUILD/classes" \
  com.roadzos.graphics.HostBridgeTest "$CORE/assets/rvhl"
