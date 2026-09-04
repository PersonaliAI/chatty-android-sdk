// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // Needed because :chatty-sdk (included from the sibling chatty-android-sdk
    // repo — see settings.gradle.kts) is a library module.
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
}