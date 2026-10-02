plugins {
    id("com.android.application")
}

android {
    namespace = "com.buzz.metronome"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.buzz.metronome"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// No third-party libraries: the app uses only the Android framework.
