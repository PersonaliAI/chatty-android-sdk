# Consumer ProGuard/R8 rules for apps that depend on this SDK with
# minification enabled. These ship inside the published AAR via
# consumerProguardFiles and apply automatically to any consuming app —
# consumers don't need to add anything themselves.

# ChattyVoiceCallScreen references LiveKit's Android SDK, but LiveKit is a
# compileOnly dependency of this module (see chatty-sdk/build.gradle.kts) so
# that apps not using voice calls don't pull in LiveKit's large WebRTC stack.
# Consumers who skip the voice feature won't have io.livekit.** on their
# classpath at all, which makes R8 emit "missing class" warnings/errors for
# those references unless told they're intentionally optional.
-dontwarn io.livekit.**
-dontwarn livekit.**
-dontwarn org.webrtc.**

# Keep this SDK's public API (classes, constructors, and public/protected
# members) intact under minification so app code compiled against one
# version of the AAR keeps working if R8 would otherwise rename or strip
# symbols only reached indirectly (e.g. via the Compose runtime or
# reflection-based tooling in a consumer's own build).
-keep public class com.personaliai.chatty.** {
    public protected *;
}

# Preserve Kotlin metadata so reflection-based libraries a consumer app may
# use (serialization, DI frameworks, etc.) can still introspect this SDK's
# Kotlin classes correctly after minification.
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keep class kotlin.Metadata { *; }
