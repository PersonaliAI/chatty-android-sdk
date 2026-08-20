plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "com.personaliai.chatty"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        targetSdk = 34
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
        // ChattyViewModel uses java.time.Instant (API 26+); desugaring
        // brings it down to this module's actual minSdk of 24.
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}

mavenPublishing {
    // Only configure the actual Sonatype publish + signing steps when
    // credentials are present. Both DSL calls validate/require credentials
    // at Gradle CONFIGURATION time (not just when the publish task runs),
    // so on a build environment without them - like JitPack, which just
    // needs the project to configure and produce a local Maven artifact,
    // not push to Sonatype - calling them unconditionally breaks the build
    // entirely before a single line of Kotlin gets compiled.
    val hasSonatypeCredentials =
        (findProperty("mavenCentralUsername") != null && findProperty("mavenCentralPassword") != null) ||
        (System.getenv("ORG_GRADLE_PROJECT_mavenCentralUsername") != null &&
            System.getenv("ORG_GRADLE_PROJECT_mavenCentralPassword") != null)
    if (hasSonatypeCredentials) {
        publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
        signAllPublications()
    }

    coordinates("com.personaliai", "chatty-android-sdk", "1.0.4")

    pom {
        name.set("Chatty Android SDK")
        description.set("Official Android SDK for Chatty AI chatbots — Kotlin + Jetpack Compose")
        url.set("https://github.com/PersonaliAI/chatty-android-sdk")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("damayantha")
                name.set("Damayantha")
                email.set("damayanthakat@gmail.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/PersonaliAI/chatty-android-sdk.git")
            developerConnection.set("scm:git:ssh://github.com/PersonaliAI/chatty-android-sdk.git")
            url.set("https://github.com/PersonaliAI/chatty-android-sdk")
        }
    }
}
