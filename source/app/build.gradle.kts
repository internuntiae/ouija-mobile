plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.ouija_mobile"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.ouija_mobile"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Core
    implementation(libs.androidx.activity.ktx)
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    // OkHttp (HTTP + WebSocket)
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    // Gson (JSON parsing)
    implementation("com.google.code.gson:gson:2.14.0")
    // EncryptedSharedPreferences (secure session storage)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}