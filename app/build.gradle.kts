
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
        versionCode = 1
        versionName = "1.0"
    }
}
