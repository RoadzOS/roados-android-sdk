#!/usr/bin/env bash
# Builds Skia for the Android ABIs the SDK ships (arm64-v8a, x86_64) with the
# core's scripts/bootstrap_skia.sh. Run once after cloning, and again after
# the core submodule changes its Skia pin. Gradle then builds the core and the
# JNI bridge (roados/src/main/cpp) against these libraries.
#
# The NDK is found from ANDROID_NDK_HOME, or <sdk>/ndk/<version> where <sdk>
# comes from ANDROID_HOME / ANDROID_SDK_ROOT / local.properties (sdk.dir) and
# <version> is the ndkVersion in roados/build.gradle.kts.
#
# Environment: ROADOS_SKIA_DIR (shared Skia checkout; default
# external/roados-graphics-core/third_party/skia).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CORE="$ROOT/external/roados-graphics-core"
[[ -f "$CORE/scripts/bootstrap_skia.sh" ]] || { echo "core submodule missing: git submodule update --init" >&2; exit 1; }

NDK_VERSION="$(sed -nE 's/.*ndkVersion = "([^"]+)".*/\1/p' "$ROOT/roados/build.gradle.kts")"
if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -z "$SDK" && -f "$ROOT/local.properties" ]]; then
    SDK="$(sed -nE 's/^sdk\.dir=(.*)$/\1/p' "$ROOT/local.properties")"
  fi
  [[ -n "$SDK" ]] || { echo "Set ANDROID_HOME (or ANDROID_NDK_HOME)" >&2; exit 1; }
  export ANDROID_NDK_HOME="$SDK/ndk/$NDK_VERSION"
fi
[[ -d "$ANDROID_NDK_HOME" ]] || {
  echo "NDK not found at $ANDROID_NDK_HOME. Install it with:" >&2
  echo "  sdkmanager \"ndk;$NDK_VERSION\"" >&2
  exit 1
}
export ROADOS_SKIA_DIR="${ROADOS_SKIA_DIR:-$CORE/third_party/skia}"

for target in android-arm64 android-x64; do
  "$CORE/scripts/bootstrap_skia.sh" Release "$target"
done
echo "Skia for Android is ready in $ROADOS_SKIA_DIR/out (NDK $(basename "$ANDROID_NDK_HOME"))."
