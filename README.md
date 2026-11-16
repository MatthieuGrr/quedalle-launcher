# Quedalle

A minimal, tile-based Android home screen launcher built with Jetpack Compose.

> *Quedalle* — French slang for "absolutely nothing". Because that's how much bloat this launcher has.

## Features

- **App tiles** — a fixed grid of your essential apps, text only
- **Shortcut tiles** — pin an app's shortcut (a conversation, a route), a contact or a web link
- **Info tiles** — time, date, battery level, next alarm, as plain text
- **Spacer tiles** — colored blank tiles to organize your grid
- **Divider tiles** — thin horizontal lines as visual separators
- **Drag & drop** — long-press any tile to reorder
- **Layouts** — several grids (work, weekend…) you switch between by hand
- **Appearance** — global tile style plus per-tile overrides: background & text colors (presets or custom HSL) and textures (iridescent, gradient, glass), edited live from a single long-press sheet
- **Tile text** — adjustable size, centered or left-aligned, normal or bold
- **Light & dark theme** — follows the system, or forced in settings
- **Search** — accent-insensitive inline search of installed apps
- **Rename tiles** — give pinned apps your own labels
- **Hide apps** — keep apps out of search results
- **Work profile** — apps from all profiles, with badged labels
- **Private space** (Android 15+) — its apps show only while it is unlocked
- **Gestures** — swipe down, swipe up and double tap, each bound to an action or an app
- **Backup** — export/import your layouts as a JSON file
- **No internet, no accounts, no tracking**

The home screen is intentionally finite: it holds exactly what fits the grid
you configured, nothing more. No pages, no app drawer, no widgets.

## Screenshots

| EN | FR |
|---|---|
| ![English screenshot](fastlane/metadata/android/en-US/images/phoneScreenshots/Screenshot_en-US.png) | ![French screenshot](fastlane/metadata/android/fr-FR/images/phoneScreenshots/Screenshot_fr-FR.png) |

## Install

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/dev.mlg.quedalle/)

Or grab the APK from the [releases page](https://github.com/MatthieuGrr/quedalle-launcher/releases)
(release APKs are unsigned — F-Droid builds and signs its own).

## Requirements

- Android 8.0 (API 26) or higher

## Permissions

| Permission | Why |
|---|---|
| `REQUEST_DELETE_PACKAGES` | Offer "Uninstall" in the app long-press menu |
| `EXPAND_STATUS_BAR` | Open the notification shade or quick settings from a gesture |
| `ACCESS_HIDDEN_PROFILES` | List the apps of the Android 15 private space while it is unlocked |

All three are `normal`-level permissions (auto-granted, read nothing). No internet
permission, no contacts permission, no notification access, no analytics.

**Lock-screen gesture.** Android only lets an accessibility service turn the
screen off. Quedalle ships one for that single purpose: it is disabled until
you enable it yourself, subscribes to no events and cannot read window content.
If you never bind a gesture to "Lock screen", it never runs.

**Contact tiles** use the system contact picker, which hands over the one
contact you choose — Quedalle cannot read your address book.

## Build from source

```bash
git clone https://github.com/MatthieuGrr/quedalle-launcher.git
cd quedalle-launcher
./gradlew assembleRelease
```

Output APK: `app/build/outputs/apk/release/app-release-unsigned.apk`

Run the unit tests with `./gradlew test`.

## License

[GPL-3.0-only](LICENSE) — Copyright (C) 2024 Matthieu Georger
