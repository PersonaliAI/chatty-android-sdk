# Contributing to Chatty Android SDK

Thanks for considering a contribution — patches, bug reports, and design-parity
fixes against the [web widget](https://github.com/PersonaliAI/chatty) are all
welcome.

## Development setup

```bash
git clone https://github.com/PersonaliAI/chatty-android-sdk.git
cd chatty-android-sdk
./gradlew build
```

Requires JDK 17+ and the Android SDK (API 34) — Gradle will download
everything else. The `example-app/` module is the fastest way to see a change
end to end:

```bash
./gradlew :example-app:installDebug
```

## Project structure

```
chatty-sdk/src/main/java/com/personaliai/chatty/
  ChattyApi.kt              HTTP client for /api/widget/*
  ChattySession.kt          Persistent session id (SharedPreferences-backed)
  ChattyViewModel.kt        Conversation state, polling, streaming
  ChattyDesignTokens.kt     The 10 widget designs' colors/radii
  ChattyChatScreen.kt       Full chat screen (Jetpack Compose)
  ChattyLauncher.kt         Floating button + full-screen dialog
example-app/                Minimal Compose app consuming the SDK
```

## Keeping design parity with the web widget

`ChattyDesignTokens.kt` is a hand-ported mirror of
[`globals.css`](https://github.com/PersonaliAI/chatty/blob/main/frontend/src/app/globals.css)'s
`.style-*` rules. If a design's colors change on web, the same values need
updating here — there's no shared source of truth across languages (yet).
Cross-check against the web repo before opening a PR that touches these
values.

## Testing

```bash
./gradlew test          # unit tests
./gradlew connectedCheck # instrumented tests, needs a device/emulator
```

There's no compiled-and-verified test suite in this repo yet — changes are
currently reviewed by hand and against `example-app/`. If you're adding a
non-trivial change, a test covering it is very welcome.

## Pull requests

- Keep PRs scoped to one change — a design fix and a new feature should be
  two PRs, not one.
- Explain *why*, not just *what*, in the description — especially for
  anything touching design tokens or the API client's request shape.
- CI (see `.github/workflows/ci.yml`) must pass before merge.

## Reporting bugs

Open an issue with: the SDK version, Android API level, a minimal repro
(ideally as a diff against `example-app/`), and what you expected vs. what
happened.
