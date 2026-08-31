import java.time.Duration

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
        consumerProguardFiles("consumer-rules.pro")
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

    testOptions {
        unitTests {
            // ChattySessionTest/ChattyDesignTokensTest touch real Android framework
            // classes (SharedPreferences, android.graphics.Color) via Robolectric
            // rather than the "unmocked android.jar throws" default.
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
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
    // Google's own full-Unicode emoji picker (search, categories, skin tones,
    // recent) — replaces the old hand-picked 60-emoji grid. Local/bundled
    // data, no network calls.
    implementation("androidx.emoji2:emoji2-emojipicker:1.6.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // compileOnly, not implementation: ChattyVoiceCallScreen is opt-in — only
    // an app that actually renders it needs LiveKit's (large, WebRTC-based)
    // Android SDK pulled in. compileOnly makes it resolvable for this
    // module's own compilation without bundling it into the AAR or forcing
    // it on every consumer; an app using voice calls must add
    // implementation("io.livekit:livekit-android:...") itself.
    compileOnly("io.livekit:livekit-android:2.18.2")

    // Test-only. JUnit4 for the runner; MockK for mocking Context/SharedPreferences;
    // kotlinx-coroutines-test for suspend fun tests; MockWebServer to exercise
    // ChattyClient's real OkHttp request/response path without hitting the network;
    // Robolectric to back org.json / android.graphics.Color / SharedPreferences with
    // real implementations instead of the "not mocked" stubs plain JVM unit tests get.
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.11")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
}

// A CI run once sat on :chatty-sdk:testDebugUnitTest for 3+ hours with zero
// output before being manually cancelled, and a second run (after the timeout
// below was added) hit the 15-minute cap the same way — no per-test output at
// all either time, so the root cause is still unconfirmed (Robolectric's
// first-use android-all-instrumented jar download stalling is one candidate,
// but a real deadlock in one of the tests is equally possible). Gradle's
// default test logging is silent until the whole task finishes, which is
// exactly why the last run gave zero diagnostic signal — showStandardStreams
// + started/passed/skipped/failed events make the next hang show exactly
// which test class/method it's stuck on instead of another opaque timeout.
tasks.withType<Test> {
    timeout.set(Duration.ofMinutes(15))
    testLogging {
        events("started", "passed", "skipped", "failed")
        showStandardStreams = true
    }
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

    coordinates("com.personaliai", "chatty-android-sdk", "1.1.0")

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
