# ⚡ RetroAmp - Native Android Music Player

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org)
[![API](https://img.shields.io/badge/API-26%2B-orange.svg)](https://android.com)

> **Developed by Anoop Kumar**  
> An authentic retro Hi-Fi cassette deck music player engineered with 100% native Kotlin and Android Jetpack.

---

## 📻 Key Features

- **📼 Authentic Cassette Tape Deck**: Custom vector-rendered cassette mechanism with dual rotating reel hubs, magnetic tape spools, and drive teeth synchronized to audio playback.
- **🎨 Rich Era-Based Skins**:
  - *Classic 1997 Winamp* (Matrix green `#00FF66`, dark graphite chassis)
  - *Cyberpunk 2077* (Neon yellow `#FCEE09`, vivid magenta `#FF007F`)
  - *Synthwave 1984* (Cyan `#00E5FF`, hot pink `#FF0055`)
  - *Emerald Hi-Fi Studio* (Deep emerald `#10B981`, gold accents `#F59E0B`)
- **📊 Real-Time Visualizer & VU Meters**:
  - Multi-band spectrum visualizer with peak hold needles.
  - Dual Stereo VU meters (Left/Right dB channels).
- **🎛 5-Band Equalizer & Bass Boost**:
  - Hardware-accelerated audio effects via Android `AudioEffect`.
  - Dynamic frequency adjustments with skin-themed sliders.
- **🔔 Interactive Notification & Quick Settings Media Player**:
  - Integrated with `MediaSessionCompat` and Android `MediaStyle`.
  - Promoted to Android's Quick Settings media carousel.
  - Directly operable transport controls: **Previous (`⏮`)**, **Play / Pause (`▶`/`⏸`)**, and **Next (`⏭`)**.
  - Dynamic album art and real-time waveform scrubber.
- **⚡ Rock-Solid Stability**:
  - Zero-lag playback debouncing prevents stuttering during rapid button taps.
  - Partial WakeLock support for uninterrupted background playback.
  - Smooth seekbar tracking that updates audio only on touch release.
- **📂 Offline Audio Library**:
  - Instant MediaStore storage scanner for MP3, FLAC, WAV, M4A, AAC, and OGG.
  - Instant format filter chips and real-time search filtering.

---

## 🛠 Tech Stack

- **Language**: Kotlin
- **Architecture**: Service-oriented playback (`MusicService`) bound to `MainActivity`
- **Media Engine**: Android `MediaPlayer` + `MediaSessionCompat`
- **UI Components**: Custom Canvas Views (`CassetteDeckView`, `VisualizerView`), ViewBinding
- **Build System**: Gradle 8.10+ / Android Gradle Plugin 8.7+

---

## 🚀 Getting Started

1. Clone the repository:
   ```bash
   git clone https://github.com/rajotiasaab-ai/RetroAmp.git
   ```
2. Open the project in **Android Studio** (Koala / Ladybug or newer).
3. Connect your Android device or start an emulator (API 26+).
4. Run the app:
   ```bash
   ./gradlew installDebug
   ```

---

## 📜 Credits

- **Developer**: Anoop Kumar
- **Project**: RetroAmp Music Player
