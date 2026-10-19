plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.aikeyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.aikeyboard"
        minSdk = 24
        targetSdk = 35
    versionCode = 9
    versionName = "0.9.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

dependencies {
    // Phase 1 intentionally has no runtime dependencies on prediction, AI, or UI toolkits.
    testImplementation("junit:junit:4.13.2")
}
