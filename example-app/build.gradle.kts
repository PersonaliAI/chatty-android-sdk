plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.personaliai.chatty.example"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.personaliai.chatty.example"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        // chatty-sdk uses java.time APIs desugared down to its minSdk 24 —
        // any app depending on it must also opt in, or the AAR metadata
        // check fails the build. See chatty-sdk/build.gradle.kts.
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    // In a real consuming app this would be a JitPack/Maven Central
    // coordinate (see the root README) — this example builds against the
    // SDK module directly so it always exercises the exact code in this repo.
    implementation(project(":chatty-sdk"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
}
