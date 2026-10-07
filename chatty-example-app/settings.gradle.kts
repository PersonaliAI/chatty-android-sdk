pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Chatty"
include(":app")

// chatty-sdk lives in the sibling chatty-android-sdk repo root (this
// project is its dogfooding example app, built by Android Studio's own
// wizard so it starts from known-current-compatible Gradle/AGP/JDK
// defaults) — built from source here, not fetched from a Maven coordinate.
include(":chatty-sdk")
project(":chatty-sdk").projectDir = File(rootDir, "../chatty-sdk")
 
