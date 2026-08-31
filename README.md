<div align="center">

# Chatty Android SDK

**Native Jetpack Compose chat UI for [Chatty](https://github.com/PersonaliAI/chatty) — zero WebView, zero compromise.**

Drop a fully native, on-brand support chat into any Android app in minutes. Talks directly to
the same `/api/widget/*` backend as the Chatty web widget, and renders every bubble, avatar, and
composer with real Compose UI — fast, themeable, and indistinguishable from the rest of your app.

[![CI](https://github.com/PersonaliAI/chatty-android-sdk/actions/workflows/ci.yml/badge.svg)](https://github.com/PersonaliAI/chatty-android-sdk/actions/workflows/ci.yml)
[![Release](https://jitpack.io/v/PersonaliAI/chatty-android-sdk.svg)](https://jitpack.io/#PersonaliAI/chatty-android-sdk)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![minSdk 24](https://img.shields.io/badge/minSdk-24-brightgreen.svg)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)](#requirements)
[![Stars](https://img.shields.io/github/stars/PersonaliAI/chatty-android-sdk?style=social)](https://github.com/PersonaliAI/chatty-android-sdk/stargazers)

[Install](#install) · [Quick start](#quick-start) · [Design gallery](#design-gallery) · [API reference](#api-reference) · [Example app](#example-app)

</div>

---

## Why this SDK

| | |
|---|---|
| **No WebView, anywhere** | Every bubble, avatar, and the composer are real `@Composable`s — no iframe, no JS bridge, no WebView memory overhead. |
| **Matches your dashboard automatically** | Fetches the bot's theme and renders with the exact colors, corner radii, and launcher shape chosen in the dashboard — no manual styling. |
| **Two integration shapes** | A floating [`ChattyLauncher`](#chattylauncher) bubble + dialog, or an embedded [`ChattyChatScreen`](#chattychatscreen) inside your own layout. |
| **A real composer, not a stub** | Full-Unicode emoji picker (search + categories + skin tones, via androidx.emoji2), animated attach menu (camera, gallery, documents, location), and mic-to-text voice notes — built in, not bolted on. |
| **Small dependency footprint** | OkHttp, Coil, and Jetpack Compose Material3. Nothing else. |

## Install

> [!NOTE]
> **Use `v1.1.0` or later.** `v1.0.0`–`v1.0.2` predate fixes that were needed for the SDK, the
> example app, and CI to actually build cleanly. Full history in the
> [releases](https://github.com/PersonaliAI/chatty-android-sdk/releases) — every tag from
> `v1.0.3` onward is CI-verified green before it ships.

### Via JitPack — works today, no account needed

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.github.PersonaliAI:chatty-android-sdk:v1.1.0")
}
```

<details>
<summary><strong>Via Maven Central</strong> (published, but currently lagging behind JitPack)</summary>

<br>

Live at `com.personaliai:chatty-android-sdk`, but stuck at `1.0.0` — later releases haven't been
pushed through `release.yml` yet. Use the JitPack coordinate above for the latest fixes until this
catches up.

```kotlin
dependencies {
    implementation("com.personaliai:chatty-android-sdk:1.0.0")
}
```

</details>

<details>
<summary><strong>As a local module</strong> (building from source)</summary>

<br>

```kotlin
// settings.gradle.kts
include(":chatty-sdk")
project(":chatty-sdk").projectDir = file("../chatty-android-sdk/chatty-sdk")
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":chatty-sdk"))
}
```

</details>

## Quick start

Find your bot ID in the Chatty dashboard under **Embed & Integrate → Android SDK**.

**Floating launcher** *(recommended)* — a bubble that expands into a full-screen chat dialog, the
native equivalent of the web widget's launcher button:

```kotlin
@Composable
fun AppRoot() {
    Box(Modifier.fillMaxSize()) {
        // ...your app content...
        ChattyLauncher(botId = "YOUR_BOT_ID")
    }
}
```

**Embedded full-screen chat** — place it directly in your own navigation, e.g. as a "Support" tab:

```kotlin
@Composable
fun SupportScreen() {
    ChattyChatScreen(botId = "YOUR_BOT_ID", modifier = Modifier.fillMaxSize())
}
```

## Design gallery

The SDK ships all 10 Chatty widget designs as Compose color/radius tokens, ported 1:1 from the
web widget's `globals.css`, so a native screen looks like whatever design is chosen in the
dashboard rather than one generic look. No configuration required — the SDK fetches the bot's
theme and resolves the matching token set automatically, including legacy `widget_style` IDs
from older presets.

| Design | Accent |
|---|---|
| `minimal` | ![#1c1a15](https://img.shields.io/badge/%20-1c1a15?style=flat-square&color=1c1a15) |
| `playful` | ![#ff8a5c](https://img.shields.io/badge/%20-ff8a5c?style=flat-square&color=ff8a5c) |
| `corporate` | ![#1c2e4a](https://img.shields.io/badge/%20-1c2e4a?style=flat-square&color=1c2e4a) |
| `dark-sleek` | ![#00e5c7](https://img.shields.io/badge/%20-00e5c7?style=flat-square&color=00e5c7) |
| `gradient-glow` | ![#a855f7](https://img.shields.io/badge/%20-a855f7?style=flat-square&color=a855f7) |
| `glassmorphism` | ![#8f6ff0](https://img.shields.io/badge/%20-8f6ff0?style=flat-square&color=8f6ff0) |
| `ecommerce` | ![#0f9d8c](https://img.shields.io/badge/%20-0f9d8c?style=flat-square&color=0f9d8c) |
| `healthcare-calm` | ![#6f9c7d](https://img.shields.io/badge/%20-6f9c7d?style=flat-square&color=6f9c7d) |
| `neubrutalism` | ![#ff3d67](https://img.shields.io/badge/%20-ff3d67?style=flat-square&color=ff3d67) |
| `luxury-editorial` | ![#161412](https://img.shields.io/badge/%20-161412?style=flat-square&color=161412) |

Font pairing (each web design uses a distinct Google Font) is intentionally out of scope for
this release; color, radius, and header/bubble treatment carry most of a design's identity.

## API reference

### `ChattyLauncher`

```kotlin
@Composable
fun ChattyLauncher(
    botId: String,
    baseUrl: String = CHATTY_DEFAULT_BASE_URL,
    host: String? = null,
    position: ChattyPosition = ChattyPosition.BOTTOM_END,
    color: Color? = null,
    onVoiceCallPress: (() -> Unit)? = null,
    onNotificationBellPress: (() -> Unit)? = null,
    enableVoiceNotes: Boolean = true,
    enableNotificationBell: Boolean = true,
    enableLocationSharing: Boolean = true,
)
```

| Param | Description |
|---|---|
| `botId` | **Required.** Your bot's ID from the dashboard. |
| `baseUrl` | Chatty backend base URL. Defaults to the production API. |
| `host` | Advisory only — sent to the backend but not used for access control. See [Notes](#notes). |
| `position` | Corner the bubble docks to. Default `BOTTOM_END`. |
| `color` | Overrides the launcher color. Defaults to the active design's accent color. |
| `onVoiceCallPress` | Forwarded to `ChattyChatScreen`'s header voice-call button. See [Notes](#notes). |
| `onNotificationBellPress` | Forwarded to `ChattyChatScreen`'s header notification bell. See [Notes](#notes). |
| `enableVoiceNotes` | Forwarded to `ChattyChatScreen`. See [Permissions](#permissions). |
| `enableNotificationBell` | Forwarded to `ChattyChatScreen`. See [Permissions](#permissions). |
| `enableLocationSharing` | Forwarded to `ChattyChatScreen`. See [Permissions](#permissions). |

### `ChattyChatScreen`

```kotlin
@Composable
fun ChattyChatScreen(
    botId: String,
    baseUrl: String = CHATTY_DEFAULT_BASE_URL,
    host: String? = null,
    hostKey: String = "app",
    modifier: Modifier = Modifier,
    onMessage: ((ChattyMessage) -> Unit)? = null,
    onVoiceCallPress: (() -> Unit)? = null,
    onNotificationBellPress: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    enableVoiceNotes: Boolean = true,
    enableNotificationBell: Boolean = true,
    enableLocationSharing: Boolean = true,
)
```

| Param | Description |
|---|---|
| `botId` | **Required.** Your bot's ID from the dashboard. |
| `baseUrl` | Chatty backend base URL. Defaults to the production API. |
| `host` | Advisory only — sent to the backend but not used for access control. See [Notes](#notes). |
| `hostKey` | Storage key used to namespace the locally persisted conversation. |
| `modifier` | Standard Compose `Modifier` for sizing/placement. |
| `onMessage` | Called for every inbound message — useful for unread badges or analytics. |
| `onVoiceCallPress` | Header voice-call button tapped. Only shown when the bot's dashboard has voice enabled. See [Notes](#notes). |
| `onNotificationBellPress` | Header notification-bell button tapped, after the OS permission prompt resolves. See [Notes](#notes). |
| `onClose` | Renders a close (✕) button in the header when set. `ChattyLauncher` passes this for you; set it yourself only if you're embedding `ChattyChatScreen` directly inside your own dialog/sheet. |
| `enableVoiceNotes` | Default `true`. Set `false` to hide the composer's mic button — the SDK then never requests `RECORD_AUDIO` at all. See [Permissions](#permissions). |
| `enableNotificationBell` | Default `true`. Set `false` to hide the header's bell button — the SDK then never requests `POST_NOTIFICATIONS` at all. See [Permissions](#permissions). |
| `enableLocationSharing` | Default `true`. Set `false` to hide the attach menu's Location option — the SDK then never requests `ACCESS_COARSE_LOCATION` at all. See [Permissions](#permissions). |

### Notes

<details open>
<summary><strong id="permissions">Permissions — what this SDK declares, and how to opt out</strong></summary>

<br>

The SDK's own `AndroidManifest.xml` declares three dangerous/runtime-gated permissions, all of
which get merged into your app's manifest automatically by the Android Gradle Plugin. Camera and
Photo Library/Documents aren't in this table — they need no permission at all (system camera
intent, Storage Access Framework pickers):

| Permission | Risk | Used for | Requested when |
|---|---|---|---|
| `RECORD_AUDIO` | Dangerous | Composer mic button → voice-note transcription | User taps the mic button, only if `enableVoiceNotes` (default `true`) |
| `POST_NOTIFICATIONS` | Runtime-gated (Android 13+) | Header bell button → local notification-permission ask | User taps the bell, only if `enableNotificationBell` (default `true`) |
| `ACCESS_COARSE_LOCATION` | Dangerous | Attach menu's Location option → drops a Google Maps link into the composer text | User taps Location, only if `enableLocationSharing` (default `true`) |

The SDK **only ever calls the OS permission dialog in direct response to that specific button
being tapped** — never on load, never speculatively. If you don't want your app requesting one of
these at all, set the matching `enable*` param to `false`; the button disappears and the SDK will
never touch that permission at runtime, regardless of what's declared in the manifest. Your app
remains free to request `RECORD_AUDIO`/`POST_NOTIFICATIONS` itself, on its own schedule, for its
own purposes (this SDK's opt-out only stops *this SDK* from requesting them).

If you also need the permission gone from your app's own merged manifest (e.g. for a Play Store
Data Safety form, or a security review that flags any declared dangerous permission), add an
explicit removal to your app's `AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <uses-permission android:name="android.permission.RECORD_AUDIO" tools:node="remove" />
</manifest>
```

Do this only alongside the matching `enable*` param set to `false` (e.g. `enableVoiceNotes =
false` for `RECORD_AUDIO`) — removing a manifest permission while still showing its button leaves
a dead button whose permission request will always fail.

The voice-call feature (`ChattyVoiceCallScreen`, opt-in, requires LiveKit) needs its own
`RECORD_AUDIO` request — see [Voice-call button](#notes) below; it's independent of the composer
mic button and isn't controlled by `enableVoiceNotes`.

</details>

<details open>
<summary><strong>Security — <code>bot_id</code> and domain restriction</strong></summary>

<br>

`bot_id` is not a secret — it's extractable from any client, web or mobile. Domain restriction
(`allowed_domains` in the dashboard) is enforced by the backend as a **rate-limit tier**, not a
hard reject: verified web traffic gets 30 msgs/60s per bot+IP, everything else (including all
mobile SDK traffic — there's no way for a native app to obtain a "verified" token the way a
browser's `Referer` allows) gets throttled to 5 msgs/120s. The `host` param this SDK sends is
advisory only and isn't used for access control. If your bot is mobile-primary, leave
`allowed_domains` empty to get the normal 30/60s tier instead.

</details>

<details>
<summary><strong>Notification bell — what it does and doesn't do</strong></summary>

<br>

Tapping it requests the OS notification permission (Android 13+ only — older versions grant it
at install time) and then calls `onNotificationBellPress`. That's as far as this SDK goes.
Actually *delivering* a push when a reply arrives while the app is backgrounded needs a push
provider wired up at the app level — either Firebase Cloud Messaging directly (free, no third
party) or a wrapper like OneSignal (adds a dashboard/API for managing sends, at the cost of
another vendor). Either way it's the same shape of work: register the device's push token, send
it to your own backend, store it against the session/user, and have your backend call
FCM/OneSignal's send API when a new assistant/agent message lands for a session that isn't
actively polling. None of that exists yet — it's backend work in `chatty-backend`, not something
this client SDK can add on its own.

</details>

<details>
<summary><strong>Voice-call button</strong></summary>

<br>

Only shown when the bot's dashboard has voice enabled, and fires `onVoiceCallPress`. This SDK now
ships a ready-to-render call screen, `ChattyVoiceCallScreen` — render it yourself from that
callback (it's opt-in: only apps that use it need LiveKit's Android SDK pulled in):

```kotlin
// build.gradle.kts (your app module)
dependencies {
    implementation("io.livekit:livekit-android:2.18.2") // or newer
}
```

```kotlin
// Application.onCreate(), once:
LiveKit.init(applicationContext)
```

```kotlin
var showCall by remember { mutableStateOf(false) }
if (showCall) {
    ChattyVoiceCallScreen(
        client = client,
        sessionId = sessionId, // the same session id ChattyChatScreen/ChattyViewModel is using
        widgetStyle = state.theme?.widgetStyle,
        onClose = { showCall = false },
    )
} else {
    ChattyChatScreen(state = state, onVoiceCallPress = { showCall = true }, /* ... */)
}
```

Also add `<uses-permission android:name="android.permission.RECORD_AUDIO" />` to your
`AndroidManifest.xml` and request it at runtime before the call screen is shown.

</details>

- Lead capture and meeting booking happen conversationally (the assistant decides to ask/act) —
  there's no separate REST call to trigger them from the SDK.
- Polling for human-agent takeover messages runs every 4s while `ChattyChatScreen` is composed,
  matching the web widget's behavior.
- Conversation history is persisted locally (`SharedPreferences`), mirroring the web widget's
  `localStorage` cache, so a returning user sees their prior messages.

## Example app

[`example-app/`](example-app) is a minimal, runnable Compose app demonstrating both integration
styles side by side — open it in Android Studio, hit run, and try the floating launcher and the
embedded full-screen chat against a live demo bot.

```bash
./gradlew :example-app:installDebug
```

## Requirements

- `minSdk 24+`
- Kotlin, Jetpack Compose (Material3)
- OkHttp, Coil (image loading) — pulled in automatically as transitive dependencies
- **[Core library desugaring](https://developer.android.com/studio/write/java8-support#library-desugaring)
  enabled in your app module** — the SDK uses `java.time` APIs desugared down to `minSdk 24`, and
  the Android Gradle Plugin enforces that any consumer of an AAR built this way opts in too:

  ```kotlin
  // app/build.gradle.kts
  android {
      compileOptions {
          isCoreLibraryDesugaringEnabled = true
      }
  }
  dependencies {
      coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
  }
  ```

---

<div align="center">

**[Contributing](CONTRIBUTING.md)** — bug reports, design-parity fixes, and PRs are welcome.

Licensed under [MIT](LICENSE) © PersonaliAI

</div>
