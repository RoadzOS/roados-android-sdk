# RoadOS Android SDK

Kotlin, Jetpack Compose and Android View bindings for [RoadOS Graphics Core](https://github.com/RoadzOS/roados-graphics-core), the C++20 + Skia engine that RoadzOS shares across platforms. The iOS SDK uses the same engine. Drawing, SVG rendering, RVHL catalogue access and hazard composition all run in C++. The JNI layer only converts values.

```kotlin
// General-purpose vector graphics
val graphics = RoadOSGraphics.create {
    width = 512
    height = 512
    background = RoadOSColor.Transparent
}
graphics.circle {
    centerX = 100f
    centerY = 100f
    radius = 40f
    fill = "#2196F3"
}
val bitmap = graphics.renderBitmap(scale = 2f)

// RoadOS hazard icons from the Visual Hazard Studio catalogue
val hazard = RoadOSHazard.create("RDM-003") {   // Large pothole
    severity = HazardSeverity.HIGH              // S4
    size = 128
    theme = RoadOSTheme.DARK
    distanceMeters = 150.0
}
val icon = hazard.toBitmap(context)
```

```kotlin
// Compose
RoadOSIcon(hazardId = "RDM-003", severity = HazardSeverity.HIGH, size = 64.dp)
```

```xml
<!-- Views -->
<com.roadzos.graphics.RoadOSHazardView
    android:layout_width="64dp" android:layout_height="64dp"
    app:roadosHazardId="RDM-003" app:roadosSeverity="s4" />
```

> **Hazard identifiers.** Look hazards up by **element ID** (`"RDM-003"`) or by an unambiguous slug (`"large_pothole"`). `"pothole"` is a *pictogram key* that 9 entries share. Looking it up throws `RoadOSException` (`NOT_FOUND`) with a message listing the candidate IDs.

## Status: read this first

**Version 0.1.0. Not built with Gradle yet. Not published.**

On the machine where this was written, the Android SDK and NDK could not be installed because there was not enough free disk space (they need about 4–5 GB). Here is what that means:

| Part | Verified? |
|---|---|
| JNI bridge (`roados/src/main/cpp/roados_jni.cpp`) and the shared C++ core | ✅ Built for the host and run on a desktop JVM by `scripts/host_jni_test.sh`. **12/12 tests pass:** rendering, error codes, UTF-16/emoji text, premultiplied images, groups and copies, catalogue lookups, hazard rendering and SVG, 2,000 create/destroy cycles, and 8-thread concurrent rendering. |
| C++ core (the same code the iOS SDK uses) | ✅ 132/132 core tests. The iOS SDK passes 16/16 on the simulator. |
| Kotlin API, Compose, View, Gradle build, Android cross-compilation of Skia | ❌ **Not compiled yet.** Kotlin compilation needs the Android toolchain. Expect small compile fixes on first build. |
| Unit tests (`roados/src/test`), instrumentation tests (`roados/src/androidTest`), sample app | ❌ Written, but **not run**. |

The version pins in `gradle/libs.versions.toml` are the latest stable releases as of 2026-11-24: AGP 9.5.2, Kotlin 2.4.21, Compose BOM 2026.11.01, NDK 30.0.16248370, Gradle 9.9.2, compileSdk 36, minSdk 24.

## Build

Requirements:
* Android SDK with NDK `30.0.16248370` and CMake `4.1.2`. Install them with `sdkmanager "ndk;30.0.16248370" "cmake;4.1.2"`.
* JDK 17 or later. Android Studio's bundled JBR works.
* Python 3 and git.
* About 6 GB of free disk space.

```bash
git clone --recurse-submodules https://github.com/RoadzOS/roados-android-sdk.git
cd roados-android-sdk
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
scripts/build_native_deps.sh                 # Skia for arm64-v8a and x86_64 (from source, ~2 min)
./gradlew :roados:assembleRelease            # AAR with libroados.so for both ABIs
./gradlew :roados:testDebugUnitTest          # JVM tests (encodings)
./gradlew :roados:connectedDebugAndroidTest  # device or emulator required
./gradlew :sample:installDebug
```

`./gradlew :roados:publishToMavenLocal` publishes `com.roadzos:roados-graphics:0.1.0` to your **local** Maven repository only. No public Maven coordinates exist.

To reuse a Skia checkout you already have, set `ROADOS_SKIA_DIR` or `roados.skiaDir` in `gradle.properties`.

### Host JNI test (no Android toolchain needed)

```bash
ROADOS_JNI_INCLUDE_DIRS=/path/to/jdk/include:/path/to/jdk/include/darwin scripts/host_jni_test.sh
```

This test builds `libroados` for macOS or Linux and runs `host-test/` (Java) against it. It needs a JDK with `jni.h`. Android Studio's JBR doesn't ship one, so point `ROADOS_JNI_INCLUDE_DIRS` at a JDK's headers. Use `;` as the separator when passing two directories through CMake.

## Sample app

`sample/` is a single-activity Compose app with four screens:
* **Graphics:** custom vector graphics with gradients, Béziers, dashes, text, a rotated group, and an RVHL hazard composed into the scene.
* **SVG:** live SVG rendering with editable markup.
* **Hazards:** all 267 catalogue entries, with search and a dark or light theme.
* **Composer:** severity, theme, representation, speed, distance and per-layer toggles, shown side by side in Compose (`RoadOSIcon`) and as a View (`RoadOSHazardView` through `AndroidView`).

`adb shell am start -n com.roadzos.sample/.MainActivity --es tab hazards` opens a specific screen.

## API overview

| Type | Purpose |
|---|---|
| `RoadOSGraphics` | Scene builder. Shapes include `circle`, `ellipse`, `rect`/`roundedRect`, `line`, `polyline`, `polygon`, `path` (SVG path data), `text`, `svg`, `image` (an `IntArray` or a `Bitmap`), `hazard`, and `group(transform, opacity, clip)`. Output: `render` (`RoadOSImage`), `renderAsync`, `renderBitmap`, `svgString`. It is `AutoCloseable`. |
| `StyleScope` | `fill`/`stroke` (hex strings), `fillColor`, `fillPaint` (`RoadOSPaint.Solid`/`LinearGradient`/`RadialGradient`), stroke width, cap, join, dash, opacity, blend mode, transform, plus `rotate`, `scale` and `translate`. |
| `RoadOSCatalogue` | `bundled(context)` unpacks the catalogue from the AAR's assets once. Lookup by ID or slug. Hashes are verified. |
| `RoadOSHazard` | An immutable `data class` with a `create(id) { … }` builder. Provides `render`, `renderAsync`, `svg` and `toBitmap(context)`. |
| `RoadOSIcon`, `RoadOSCanvas` | Compose components. They render off the main thread at the screen density. |
| `RoadOSHazardView` | An `ImageView` with XML attributes. It re-renders off the main thread. |
| `RoadOSException` | Its `code` mirrors the core's `ErrorCode`. |

**Errors.** Drawing calls never throw. The first invalid input is kept in `firstError` and thrown by `render()`.

**Memory.** Native scenes are freed by `close()` or `use {}`. If you don't free one, a phantom-reference cleaner frees it once the object is unreachable. This works on API 24, which doesn't have `java.lang.ref.Cleaner`.

**Threading.**
* Don't build the same `RoadOSGraphics` from more than one thread.
* `renderAsync` renders a snapshot on `Dispatchers.Default`.
* Catalogues and hazards are immutable and thread-safe.

## Architecture

```
Visual Hazard Studio (web) ──► RVHL bundle (core assets/rvhl, hash-verified)
                                    │  packaged as AAR assets (not copied into this repo)
RoadOS Graphics Core (C++20/Skia)   external/roados-graphics-core (submodule)
  roados_graphics · roados_rvhl · roados_binding
        │  CMake via externalNativeBuild; Skia from scripts/build_native_deps.sh
roados/src/main/cpp/roados_jni.cpp   JNI: Java values ↔ roados::binding (marshalling only)
roados/src/main/kotlin               Kotlin API, Bitmap, Compose, View
```

## Licensing

* **This repository:** no license has been chosen yet.
* **Skia:** BSD-3-Clause. The core's other dependencies are listed in its README.
* **RVHL assets:** generated from the Visual Hazard Studio, which currently declares "all rights reserved". Do not ship apps containing them until the Studio is licensed.
* **`gradle/wrapper/gradle-wrapper.jar`:** Gradle 9.9.2's official wrapper (Apache-2.0). Its SHA-256 has been checked against services.gradle.org.

RVHL is a RoadOS proposal. It is not ISO-certified or officially approved.
