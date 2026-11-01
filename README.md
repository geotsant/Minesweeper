# Minesweeper (Android)

A modern, high-performance, deterministic **No-Guess Minesweeper** app for Android. Built in 100% pure Kotlin with Jetpack Compose, Google Material 3, and a custom hardware-accelerated canvas engine.

Every generated board is **100% logically solvable** from the very first tap—no 50/50 guesses are ever required.

---

## Features

- **100% Deterministic (No-Guess) Logic**: Built-in multi-tier deductive logic solver ensures that every generated puzzle can be deduced purely through logic without random guessing.
- **Ultra-Fast Board Generation**: Instant opening generation with organic opening chunk capping (never carves out huge empty chunks).
- **Standard Dimensions**: 18 columns × 32 rows with 150 mines.
- **Nordic AMOLED Minimalist Aesthetic**:
  - Pure `#000000` AMOLED black background for maximum battery efficiency and infinite contrast.
  - Pure flat Google Material 3 (M3) architecture with zero elevation lift, no harsh borders, and smooth capsule pill geometry.
  - 7 curated earthy color themes: **Olive Gold**, **Terracotta Red**, **Slate Blue**, **Dusky Purple**, **Burnt Orange**, **Forest Green**, and **Parchment White**.
  - Harmonized typography: UI fonts and counters dynamically adopt a very light, vibrant tint of the selected theme color.
- **Fluid Multi-Touch Canvas**:
  - Simultaneous 2-finger pinch zoom and fluid panning.
  - Smooth 1-finger momentum drag with calibrated inertia fling scrolling.
  - Clean seamless keycap rendering with rounded exterior corners and subpixel edge overlap.
- **Customizable UX**:
  - Adjustable long-press delay slider (100 ms – 600 ms).
  - Quick chord actions on revealed numbers.
  - Completely silent and distraction-free (zero haptics).
- **Offline Leaderboard**: Tracks your 5 fastest completed games with date stamps.
- **Automatic State Preservation**: Saves game progress on pause or app exit and resumes seamlessly.

---

## Tech Stack & Architecture

- **Language**: 100% Pure Kotlin (zero native C/Rust dependencies)
- **UI Toolkit**: Jetpack Compose + Google Material 3 (M3)
- **Game Board**: Custom Android View (`MinesweeperBoardView`) with hardware-accelerated Canvas drawing and zero-allocation frame loops
- **Deductive Solver**: `NoGuessSolver.kt` (custom multi-tier constraint propagation and 50/50 bottleneck breaker)
- **Target SDK**: Android 15 (API 35), Min SDK: Android 8.0 (API 26)

---

## Getting Started

### Prerequisites

- [Android Studio Ladybug (or newer)](https://developer.android.com/studio)
- JDK 17 or higher
- Android SDK 35

### Clone & Build

```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/minesweeper-android.git
cd minesweeper-android

# Build the debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest
```

The compiled APK will be available at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Install directly to a connected device

```bash
./gradlew installDebug
```

---

## Project Structure

```
Minesweeper/
├── app/
│   ├── src/main/java/com/example/minesweeper/
│   │   ├── core/
│   │   │   ├── MinesweeperEngine.kt   # Core game state & fast logic engine
│   │   │   └── NoGuessSolver.kt       # Multi-tier deductive solver & generator
│   │   ├── model/
│   │   │   ├── AppColorTheme.kt       # 7 curated M3 color themes & tokens
│   │   │   ├── GameDifficulty.kt      # Board configuration (18x32, 150 mines)
│   │   │   ├── GameStateManager.kt    # Persistent game save/restore
│   │   │   └── LeaderboardManager.kt  # Local high score records
│   │   ├── ui/theme/                  # M3 Theme, Color palettes & Typography
│   │   ├── view/
│   │   │   └── MinesweeperBoardView.kt# Touch gestures, zoom/pan & Canvas rendering
│   │   └── MainActivity.kt            # Jetpack Compose navigation & screens
│   └── src/test/java/                 # Automated unit tests for solver & logic
└── build.gradle.kts
```

---

## License

This project is licensed under the [MIT License](LICENSE) — anyone is free to download, use, fork, and modify the code.
