pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // io.livekit:livekit-android transitively depends on
        // com.github.davidliu:audioswitch (a JitPack-hosted commit-hash
        // artifact, not a Maven Central release) — confirmed the hard way
        // via a real CI failure ("Could not find
        // com.github.davidliu:audioswitch:<hash>") the first time the
        // LiveKit dependency was added.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "chatty-android-sdk"
include(":chatty-sdk")
include(":example-app")
