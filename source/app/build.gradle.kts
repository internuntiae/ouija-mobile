plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.example.ouija_mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ouija_mobile"
        minSdk = 29
        targetSdk = 35
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

    // Room (local database / message cache)
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
}