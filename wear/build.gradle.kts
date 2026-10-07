import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The Wear OS app: the guided workout on the wrist, synced with the phone app over the Data Layer.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kkfittracking.watch"
    compileSdk = 36

    defaultConfig {
        // The same id as the phone app: the Data Layer only connects an app with itself.
        applicationId = "com.kkfittracking"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        // The same key as the phone app, for the same reason.
        getByName("debug") {
            storeFile = rootProject.file("signing/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// The APK is called FitTraKKing-watch-debug.apk, so the phone and watch files cannot be mixed up.
base {
    archivesName.set("FitTraKKing-watch")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":wearprotocol"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.wear)
    implementation(libs.androidx.wear.input)
    implementation(libs.androidx.wear.ongoing)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)

    implementation(libs.health.services.client)
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.material)
    implementation(libs.androidx.concurrent.futures.ktx)
    // ListenableFuture, the future type the tile and Health Services APIs return (the separate
    // listenablefuture artifact resolves to an empty placeholder when Guava is expected).
    implementation(libs.guava)

    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
}
