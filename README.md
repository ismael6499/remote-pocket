# Remote Pocket 📱🖱️

[![Android](https://img.shields.io/badge/Android-SDK%2026--34-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/)
[![Java](https://img.shields.io/badge/Java-17%20%2F%2021-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Node.js](https://img.shields.io/badge/Node.js-18+-339933?style=flat-square&logo=node.js&logoColor=white)](https://nodejs.org/)
[![C# .NET](https://img.shields.io/badge/C%23-.NET%20Framework-512BD4?style=flat-square&logo=dotnet&logoColor=white)](https://dotnet.microsoft.com/)
[![WebSocket](https://img.shields.io/badge/WebSocket-Real--Time-010101?style=flat-square&logo=socketdotio&logoColor=white)](https://developer.mozilla.org/en-US/docs/Web/API/WebSocket)
[![UDP Discovery](https://img.shields.io/badge/UDP-Zero--Touch%20LAN-4B5563?style=flat-square)](https://en.wikipedia.org/wiki/User_Datagram_Protocol)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](https://opensource.org/licenses/MIT)

**Remote Pocket** is an ultra-low-latency, zero-touch remote control and live desktop companion system pairing a native **Android client** with a lightweight **Windows background daemon**. Designed for speed, convenience, and complete local privacy, it turns your smartphone into a precision trackpad, dual-mode keyboard, multi-monitor display switcher, and system automation hub without third-party cloud intermediaries.

---

## ✨ Key Features

### 🔍 Zero-Touch LAN Discovery & Multi-PC Switching
- **UDP Broadcast Auto-Detection:** Automatically discovers all host PCs on your local Wi-Fi within milliseconds using UDP port `5051`.
- **Zero Manual IP Entry:** Switch between multiple computers (e.g. Work PC, Gaming Rig, Home Server) with a single tap from the top toolbar.
- **Remote Access (Ngrok):** Securely tunnel through firewalls and NAT via built-in Ngrok support for control outside your home network.

### 🖱️ Precision Trackpad & Intuitive Multi-Touch Gestures
- **Sub-Pixel Motion Smoothing:** Native mouse acceleration with fine tracking sensitivity.
- **Authentic Windows Cursor:** Pixel-crisp Windows pointer arrow overlay without lag or visual clutter.
- **Natural Multi-Touch Gestures:**
  - **1-Finger Tap:** Instant Left Click at current pointer coordinates.
  - **2-Finger Tap:** Right Click (context menu).
  - **3-Finger Tap:** Middle Click (open/close tabs, auto-scroll).
  - **2-Finger Drag:** Silky-smooth vertical wheel scrolling.
  - **Double-Tap & Hold:** Tap-and-half gesture to grab, drag-and-drop windows or files, releasing cleanly upon finger lift.
- **Persistent Mouse Buttons:** Dedicated dock buttons for `Left`, `Middle`, `Right`, `Hold Drag Lock`, `Double Click`, and `Wheel ▲/▼`.

### 🖥️ Full HD Live Screen Stream & Multi-Monitor Support
- **1080p High-Fidelity Streaming:** On-demand desktop JPEG streaming at up to 60 FPS directly over binary WebSocket frames.
- **Adaptive FPS Pill Selector:** Toggle between `⚡ 30 FPS` (smooth default), `⚡ 60 FPS` (high performance), `⚡ 15 FPS` (balanced), and `⚡ 2 FPS` (low bandwidth) on the fly without reconnecting.
- **Zero Drift Direct Touch:** Tap anywhere on the live video stream to dispatch clicks directly to the exact Windows desktop coordinates (`viewToDesktopCoords`).
- **Drift-Free Centered Cursor Tracking:** Zoom into any portion of your screen; moving the mouse automatically pans the viewport smoothly to keep the pointer centered.
- **Multi-Monitor Manager:** Auto-enumerates multi-head displays with native resolutions; switch active viewports seamlessly via the toolbar pill.

### ⌨️ Dual Keyboard Input Engine
- **Floating Action Button (FAB):** Draggable anywhere on screen, automatically re-clamped within safe display insets, with auto-fade transparency (`alpha = 0.20f`) after 3 seconds of inactivity.
- **Soft Keyboard Mode (Gboard / Samsung Keyboard):** Single tap on the FAB surfaces the native Android IME with autofocus for rapid text, voice typing, and mobile autocomplete.
- **Extended PC Keyboard:** Full mechanical desktop keyboard layout featuring:
  - Function keys (`F1` – `F12`), `Esc`, `Tab`, `Insert`, `Delete`, `Home`, `End`, `Page Up`, `Page Down`, Arrow keys.
  - Sticky modifier toggles (`Ctrl`, `Alt`, `Shift`, `Win`) for multi-key shortcuts.
  - One-tap quick shortcuts: `Ctrl+C`, `Ctrl+V`, `Ctrl+Z`, `Ctrl+A`, `Alt+Tab`, `Win+D`, `Alt+F4`, and Task Manager (`Ctrl+Shift+Esc`).

### 🎛️ System Controls, Multimedia & Power Automation
- **Physical Display Brightness:** Adjust hardware monitor backlight (0% – 100%) via WMI/CIM methods with real-time slider feedback.
- **Master Audio & Peak Levels:** Native Windows CoreAudioApi COM pipe for hardware volume control, instant mute toggle, and media playback control (`Play/Pause`, `Next`, `Previous`).
- **PushMonitOff Screen Off Automation:** Native hardware display power-off sequence integrating with PushMonitOff hotkeys, featuring Start menu focus management to ensure menus never remain open upon wake-up.
- **Zero-Jump Layout Stability:** Bottom sheet pre-allocates container dimensions and hydrates from local cache, preventing layout shift and accidental slider interactions.
- **Remote Power Actions:** `Lock Workstation`, `Sleep`, `Restart`, and `Shutdown` with protective confirmation prompts.
- **Custom PC Shortcuts:** Execute user-defined batch scripts and shell commands defined in `shortcuts.json`.

### 🛡️ Self-Healing Windows Service Architecture
- **Persistent Interactive Execution:** Runs silently under the user's active desktop session via Windows Explorer delegation (`run_silent.vbs`), avoiding headless service restrictions and granting full GUI injection capabilities.
- **0% Idle CPU Utilization:** Video streamer process only spawns when a client actively requests screen preview and shuts down instantly upon disconnect.
- **Automatic Auto-Reconnect:** Exponential reconnect backoff handling Wi-Fi handshakes, sleep cycles, and app backgrounding smoothly.

### 📲 Over-the-Air (OTA) Updates & Mobile Drop
- **1-Tap In-App Updates:** Check and install latest APK updates directly from the host PC agent.
- **Automated Gradle Distribution:** Gradle builds automatically deliver release APKs to `C:\Users\agust\Documents\Mobile Drop\` and the PC agent `/updates` directory.

### 🌐 Internationalization (i18n)
- Centralized multi-language architecture: **English (Default)** and **Español (Latin America)**.

---

## 🛠️ Architecture & Tech Stack

```
┌─────────────────────────────────┐                 ┌─────────────────────────────────┐
│        Android Client           │                 │       Windows PC Agent          │
│   (Java 17, Material 3, OkHttp) │                 │   (Node.js + C# InputHelper)    │
├─────────────────────────────────┤                 ├─────────────────────────────────┤
│  • MainActivity (Trackpad/Live) │  UDP :5051      │  • UDP Discovery Responder      │
│  • SystemSheet (Controls)       │ ──────────────> │  • Express REST API (:5050)     │
│  • PcKeyboardDialog (Shortcuts) │  WS :5050       │  • WebSocket Server (:5050)     │
│  • WebSocketManager (OkHttp)    │ <═════════════> │  • InputHelper.exe (Pipe C#)    │
│  • AppLogger (Live Diagnostics) │                 │  • GDI+ Streamer (DirectX/Win32)│
└─────────────────────────────────┘                 └─────────────────────────────────┘
```

- **Android Client (`android-app/`):**
  - **Language / Runtime:** Java 17 / 21, compileSdk 34, minSdk 26.
  - **UI / Styling:** Glassmorphism Dark theme (`#0B0F19`), Material Design 3, custom canvas renderers.
  - **Networking:** [OkHttp 4.12.0](https://square.github.io/okhttp/) (WebSockets & REST), [Gson 2.10.1](https://github.com/google/gson).
- **PC Agent Daemon (`pc-agent/`):**
  - **Server Engine:** [Node.js](https://nodejs.org/) (Express + `ws` WebSocket library).
  - **Native Input Driver:** `InputHelper.cs` compiled via native .NET Framework (`csc.exe`) using Win32 `SendInput`, `keybd_event`, and `CoreAudioApi` COM interfaces.
  - **Screen Capture:** GDI+ memory-buffered frame capture with adaptive JPEG compression and DPI scaling.
  - **Process Management:** VBScript / Windows Script Host (`run_silent.vbs`) for decoupled execution.

---

## 🚀 Getting Started

### Prerequisites
- **PC:** Windows 10 or Windows 11 with .NET Framework 4.5+ and [Node.js](https://nodejs.org/) (v18 or higher).
- **Phone:** Android 8.0+ (Oreo) or higher connected to the same local Wi-Fi network.

### 🖥️ 1. Running the PC Agent

1. Clone the repository on your Windows computer:
   ```bash
   git clone https://github.com/ismael6499/remote-pocket.git
   cd remote-pocket\pc-agent
   ```

2. Run the automated installer:
   ```cmd
   install.bat
   ```
   *This compiles `InputHelper.exe`, installs Node dependencies, and creates a Windows Startup shortcut so the agent starts automatically upon logon.*

3. To start or restart the agent manually in the background:
   ```powershell
   Start-Process "explorer.exe" -ArgumentList '"pc-agent\run_silent.vbs"'
   ```

### 📱 2. Installing the Android App

1. Grab the latest `RemotePocket.apk` from the [Releases](https://github.com/ismael6499/remote-pocket/releases) tab (or from `C:\Users\agust\Documents\Mobile Drop\RemotePocket.apk`).
2. Install the APK on your Android phone and grant local network permissions.
3. Open the app: **your PC will appear automatically in the top dropdown list within seconds.** Tap to connect!

---

## 📋 Gesture & Control Quick Reference

| Action | Trackpad Gesture | Live Screen View |
| :--- | :--- | :--- |
| **Move Pointer** | 1-Finger Slide | 1-Finger Slide (Direct move or Pan view) |
| **Left Click** | 1-Finger Tap | 1-Finger Tap at touched coordinate |
| **Right Click** | 2-Finger Tap | 2-Finger Tap at touched coordinate |
| **Middle Click** | 3-Finger Tap | 3-Finger Tap |
| **Vertical Scroll** | 2-Finger Slide (Up / Down) | 2-Finger Slide |
| **Drag & Drop** | Double-Tap & Slide (Hold) | Double-Tap & Slide (Hold) |
| **Toggle Keyboard** | Tap Floating Action Button (FAB) | Tap Floating Action Button (FAB) |
| **System Controls** | Tap Sliders Icon (`Tune`) in top bar | Tap Sliders Icon (`Tune`) in top bar |
| **Exit Fullscreen** | Android Gesture / Back button | Top-Right Exit button (`⛶`) or Back gesture |

---

## 🧪 Diagnostics & Verification

Remote Pocket features an in-memory logging subsystem and live diagnostic console directly in the Android app:

- **Live Log Terminal:** Tap **Diagnostics** in the PC selection menu to inspect real-time UDP broadcast packets, WebSocket handshakes, latencies, and HTTP payloads.
- **PC Status Verification:**
  ```powershell
  # Check agent status
  Invoke-RestMethod -Uri "http://localhost:5050/api/status"
  
  # Trigger silent agent reboot
  Invoke-RestMethod -Uri "http://localhost:5050/api/server/restart" -Method Post
  ```

---

## 📦 Building from Source

### Android APK Build
```bash
cd android-app
.\gradlew assembleDebug
```
The compiled APK will automatically be placed in:
- `android-app/app/build/outputs/apk/debug/app-debug.apk`
- `pc-agent/updates/app-release.apk`
- `C:\Users\agust\Documents\Mobile Drop\RemotePocket.apk`

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
