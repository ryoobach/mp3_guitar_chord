
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ryoo.mp3guitarchord"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ryoo.mp3guitarchord"
        minSdk = 23
        targetSdk = 35
        versionCode = 17
        versionName = "1.60"
    }

    signingConfigs {
        create("stableDebug") {
            storeFile = rootProject.file("signing/easy-mp3-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
