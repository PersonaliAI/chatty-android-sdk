plugins {
    // AGP 9's built-in Kotlin support replaces the standalone
    // org.jetbrains.kotlin.android plugin outright — applying that plugin
    // alongside AGP 9 is a hard error now, not just a deprecation.
    id("com.android.library") version "9.4.0" apply false
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
}
