plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.johnb.frenchspelling"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.johnb.frenchspelling"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // The bundled OCR model ships native libs for every ABI; real phones are ARM.
        // Dropping x86/x86_64 roughly halves the APK. (Re-add them for an emulator.)
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    // On-device OCR for the "scan a word list" feature (bundled model, works offline).
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
