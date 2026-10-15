plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cia.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cia.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // Keystore permanen di project (debug.keystore) — WAJIB dipakai agar
        // signature konsisten antar build. Keystore bawaan di /root/.android
        // HILANG setiap VM replacement -> INSTALL_FAILED_UPDATE_INCOMPATIBLE.
        create("ciaDebug") {
            storeFile = file("../debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("ciaDebug")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // Coroutines for moving PackageManager work off the main thread
    // (both artifacts verified present in the offline Gradle cache).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    // Provides com.google.common.util.concurrent.ListenableFuture at runtime —
    // the empty listenablefuture artifact pinned below has no classes, which crashed
    // androidx.profileinstaller on startup (ClassNotFoundException). This tiny
    // androidx library has no transitive deps (unlike full Guava, whose
    // checker-compat-qual isn't in the offline cache).
    implementation("androidx.concurrent:concurrent-futures:1.1.0")
}

// Offline build: the Gradle cache holds newer transitive artifacts than the
// POMs request, so pin everything to the cached versions.
configurations.all {
    resolutionStrategy.eachDependency {
        when (requested.group) {
            "androidx.lifecycle" -> useVersion("2.8.6")
            "org.jetbrains.kotlinx" -> if (requested.name.startsWith("kotlinx-coroutines")) useVersion("1.9.0")
        }
        when ("${requested.group}:${requested.name}") {
            "androidx.annotation:annotation" -> useVersion("1.8.1")
            "androidx.annotation:annotation-experimental" -> useVersion("1.4.1")
            "androidx.core:core" -> useVersion("1.15.0")
            "androidx.core:core-ktx" -> useVersion("1.15.0")
            "androidx.activity:activity" -> useVersion("1.9.3")
            "androidx.collection:collection" -> useVersion("1.4.2")
            "androidx.customview:customview" -> useVersion("1.1.0")
            "com.google.guava:listenablefuture" -> useVersion("9999.0-empty-to-avoid-conflict-with-guava")
        }
    }
}
