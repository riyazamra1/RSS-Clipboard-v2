# RSS Clipboard

**RSS Clipboard** is a lightweight, ultra-fast native Android clipboard manager built with Kotlin and Jetpack Compose by **Razeen Secure Solutions**.

## Key Features
- **Hybrid Retention**: Unpinned automatic captures expire in 24 hours to keep storage lightweight; pinned and manually saved clips never expire.
- **Background OS Capture**: Runs via an optimized Accessibility Service to capture text copied anywhere on your device, even when the app is fully closed.
- **Lightning-Fast Room Database**: Indexed queries ensure instant launch and zero lag, even with large clip volumes.
- **Auto-Category Detection & Horizontal Source Attribution**: Automatically categorizes Mobile, Email, Website, Password, and Text clips with distinct source app indicators.
- **Onboarding & Verification Flow**: Complete user flow featuring splash screen, animated feature slides, centered animated auth, email verification with unverified banner notification, and welcome mail simulations.

## Project Structure
```
├── app
│   ├── build.gradle.kts
│   └── src
│       └── main
│           ├── AndroidManifest.xml
│           ├── java
│           │   └── com
│           │       └── riyaz
│           │           └── rssclipboard
│           │               ├── MainActivity.kt
│           │               ├── data
│           │               │   └── ClipData.kt
│           │               ├── service
│           │               │   └── RssClipboardAccessibilityService.kt
│           │               └── ui
│           │                   └── ClipboardViewModel.kt
│           └── res
│               ├── values
│               │   ├── colors.xml
│               │   ├── strings.xml
│               │   └── themes.xml
│               └── xml
│                   └── accessibility_service_config.xml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## How to Build & Push to GitHub
1. Download `RSS-Clipboard-Project.zip` and extract it on your computer.
2. Open the extracted folder in Android Studio or GitHub Codespaces.
3. Commit and push your changes to your GitHub repository (`https://github.com/riyazamra1/RSS-Clipboard`).