plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

val coreDir = rootProject.file("external/roados-graphics-core")
val skiaDir: String = providers.gradleProperty("roados.skiaDir")
    .orElse(providers.environmentVariable("ROADOS_SKIA_DIR"))
    .getOrElse(coreDir.resolve("third_party/skia").absolutePath)

android {
    namespace = "com.roadzos.graphics"
    compileSdk = 36
    ndkVersion = "30.0.16248370"

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            // Skia is built for these ABIs by scripts/build_native_deps.sh.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DROADOS_SKIA_DIR=$skiaDir", "-DANDROID_STL=c++_static")
                cppFlags += listOf("-fexceptions", "-frtti")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    // The RVHL bundle is not copied into this repository: the AAR packages the
    // core's generated, hash-verified bundle (assets/rvhl) directly.
    sourceSets.getByName("main").assets.srcDir(coreDir.resolve("assets"))

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

// Local publishing only (`./gradlew :roados:publishToMavenLocal`).
// Nothing is published to a public repository.
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.roadzos"
            artifactId = "roados-graphics"
            version = "0.1.0"
            afterEvaluate { from(components["release"]) }
        }
    }
}
