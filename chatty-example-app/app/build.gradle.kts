import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Loaded from local.properties (gitignored — see the comment there) rather
// than hardcoded, since this is the real bot id embedded on the production
// chatty.personaliai.com landing page, not a throwaway demo id. Falls back
// to a generic public demo bot if unset, so a fresh checkout still builds.
val chattyDemoBotId: String = Properties().apply {
    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) load(FileInputStream(localProps))
}.getProperty("chatty.demoBotId", "c8fa19c8-dd25-43a3-9c55-e8099e6f532e")

android {
    // Distinct from chatty-sdk's own namespace ("com.personaliai.chatty") —
    // reusing it here would collide both modules' generated R classes.
    namespace = "com.personaliai.chatty.example"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.personaliai.chatty.example"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "CHATTY_DEMO_BOT_ID", "\"$chattyDemoBotId\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        // Must match chatty-sdk's compileOptions — the Compose BOM's
        // 2026-era libraries ship JVM-11 bytecode, and 17 is also AGP 9's
        // own minimum/default JDK. minSdk stays 24 regardless; desugaring
        // (below) is what makes newer APIs run on old devices.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // chatty-sdk uses java.time APIs desugared down to its minSdk 24 —
        // any app depending on it must also opt in, or the AAR metadata
        // check fails the build.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    // Sibling module, built from source (see settings.gradle.kts) — a real
    // consuming app would use a JitPack/Maven Central coordinate instead
    // (see the root README), but this example always exercises the exact
    // SDK code in this repo.
    implementation(project(":chatty-sdk"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}