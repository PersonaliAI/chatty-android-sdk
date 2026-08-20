# Chatty Android SDK

**Native Jetpack Compose chat UI for [Chatty](https://github.com/PersonaliAI/chatty) — no WebView.**

Drop a fully native, on-brand support chat into any Android app. The SDK talks directly to the
same `/api/widget/*` backend as the Chatty web widget and renders every message, bubble, and
composer with real Compose UI — so it's fast, themeable, and behaves like the rest of your app
instead of an embedded browser.

[![CI](https://github.com/PersonaliAI/chatty-android-sdk/actions/workflows/ci.yml/badge.svg)](https://github.com/PersonaliAI/chatty-android-sdk/actions/workflows/ci.yml)
[![Release](https://jitpack.io/v/PersonaliAI/chatty-android-sdk.svg)](https://jitpack.io/#PersonaliAI/chatty-android-sdk)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![minSdk 24](https://img.shields.io/badge/minSdk-24-brightgreen.svg)](#requirements)

---

## Contents

- [Why this SDK](#why-this-sdk)
- [Install](#install)
- [Quick start](#quick-start)
- [Design parity](#design-parity)
- [API reference](#api-reference)
- [Example app](#example-app)
- [Requirements](#requirements)
- [Contributing](#contributing)
- [License](#license)

## Why this SDK

- **No WebView.** Every bubble, avatar, and the composer are real `@Composable`s — no iframe,
  no JS bridge, no WebView memory overhead.
- **Matches your dashboard design automatically.** Whatever one of the 10 Chatty widget designs
  is selected for the bot, the SDK fetches the theme and renders with matching colors and corner
  radii — no manual styling needed. See [Design parity](#design-parity).
- **Two integration shapes.** A floating [`ChattyLauncher`](#chattylauncher) bubble + dialog, or
  an embedded [`ChattyChatScreen`](#chattychatscreen) you place directly in your own layout.
- **Small dependency footprint.** OkHttp, Coil, and Jetpack Compose Material3 — nothing else.

## Install

> **Using `v1.0.4` or later.** `v1.0.0`/`v1.0.1` never compiled at all (a Gradle configuration
> bug), and `v1.0.2` — while it built — predates the fixes that made the SDK, the example app,
> and CI itself actually compile cleanly (see the repo's commit history around the
> `PersonaliAI` org transfer for specifics: a broken coroutine block, missing dependencies, an
> API-level mismatch, and a missing CI heap size). `v1.0.3` was the first tag verified green
> end-to-end; `v1.0.4` adds structural UI parity with the web widget (header/bubble/composer
> sizing, avatars, send-button variants) and corrects stale security docs — see the
> [Release](https://github.com/PersonaliAI/chatty-android-sdk/releases) page for the build
> artifacts.

### Via JitPack (works today, no account needed)

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
    implementation("com.github.PersonaliAI:chatty-android-sdk:v1.0.4")
}
```

### Via Maven Central

Publishing is fully wired up (`com.vanniktech.maven.publish`, see
[`chatty-sdk/build.gradle.kts`](chatty-sdk/build.gradle.kts), and the tag-triggered
[`release.yml`](.github/workflows/release.yml) workflow) but not yet live — it needs a verified
Sonatype account for the `com.personaliai` namespace. Once published:

```kotlin
dependencies {
    implementation("com.personaliai:chatty-android-sdk:1.0.4")
}
```

### As a local module (building from source)

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

## Quick start

Find your bot ID in the Chatty dashboard under **Embed & Integrate → Android SDK**.

### Floating launcher (recommended)

A bubble that expands into a full-screen chat dialog — the native equivalent of the web widget's
launcher button.

```kotlin
@Composable
fun AppRoot() {
    Box(Modifier.fillMaxSize()) {
        // ...your app content...
        ChattyLauncher(botId = "YOUR_BOT_ID")
    }
}
```

### Embedded full-screen chat

Place the chat directly in your own navigation — e.g. as a "Support" tab or screen.

```kotlin
@Composable
fun SupportScreen() {
    ChattyChatScreen(botId = "YOUR_BOT_ID", modifier = Modifier.fillMaxSize())
}
```

## Design parity

The SDK ships all 10 Chatty widget designs as Compose color/radius tokens, ported 1:1 from the
web widget's `globals.css`, so a native screen looks like the design chosen in the dashboard
rather than one generic look:

`minimal` · `playful` · `corporate` · `dark-sleek` · `gradient-glow` · `glassmorphism` ·
`ecommerce` · `healthcare-calm` · `neubrutalism` · `luxury-editorial`

No configuration is required — `ChattyChatScreen` fetches the bot's theme and resolves the
matching token set automatically, including legacy `widget_style` IDs from older presets. Font
pairing (each web design uses a distinct Google Font) is intentionally out of scope for this
release; color, radius, and header/bubble treatment carry most of a design's identity.

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
)
```

| Param | Description |
|---|---|
| `botId` | **Required.** Your bot's ID from the dashboard. |
| `baseUrl` | Chatty backend base URL. Defaults to the production API. |
| `host` | Advisory only — sent to the backend but not used for access control. See [Notes](#notes). |
| `position` | Corner the bubble docks to. Default `BOTTOM_END`. |
| `color` | Overrides the launcher color. Defaults to the active design's accent color. |

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

### Notes

- **`bot_id` is not a secret** — it's extractable from any client, web or mobile. Domain
  restriction (`allowed_domains` in the dashboard) is enforced by the backend as a **rate-limit
  tier**, not a hard reject: verified web traffic gets 30 msgs/60s per bot+IP, everything else
  (including all mobile SDK traffic — there's no way for a native app to obtain a "verified"
  token the way a browser's `Referer` allows) gets throttled to 5 msgs/120s. The `host` param
  this SDK sends is advisory only and isn't used for access control. If your bot is
  mobile-primary, leave `allowed_domains` empty to get the normal 30/60s tier instead.
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

## Contributing

Bug reports, design-parity fixes, and PRs are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md)
for local setup and project structure.

## License

[MIT](LICENSE) © PersonaliAI
