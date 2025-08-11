plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Reverted to the direct plugin ID to fix the "Unresolved reference: kotlinx" error.
    // This version should match your main Kotlin compiler version.
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"
}

android {
    namespace = "com.example.ncertbookreader"
    // It's best practice to use the latest stable SDK version
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.ncertbookreader"
        minSdk = 29
        targetSdk = 34 // Should match compileSdk
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            // Enable support for vector drawables on older API levels
            useSupportLibrary = true
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
        // Target Java 17, which is the standard for modern Android projects
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        // Align the Kotlin JVM target with the Java version for compatibility
        jvmTarget = "17"
    }

    buildFeatures {
        // Enables Jetpack Compose for the project
        compose = true
    }

    composeOptions {
        // The Kotlin Compiler Extension version is managed automatically by the Compose BOM
        // kotlinCompilerExtensionVersion is not needed here
    }
}

dependencies {

    // Core AndroidX & Lifecycle Dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose Dependencies (using the BOM - Bill of Materials)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Navigation for Compose
    implementation(libs.androidx.navigation.compose)

    // KotlinX Serialization for JSON parsing
    implementation(libs.kotlinx.serialization.json)

    // Media3 (ExoPlayer) for audio/video playback
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // Testing Dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom)) // BOM for test dependencies
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Debugging Dependencies
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}