
# Turtle Launcher
![Downloads](https://img.shields.io/github/downloads/Endiq-jar/TurtleLauncher/total?style=for-the-badge&logo=github)
![Release](https://img.shields.io/github/v/release/Endiq-jar/TurtleLauncher?style=for-the-badge)
![Stars](https://img.shields.io/github/stars/Endiq-jar/TurtleLauncher?style=for-the-badge)
![Forks](https://img.shields.io/github/forks/Endiq-jar/TurtleLauncher?style=for-the-badge)

![License](https://img.shields.io/github/license/Endiq-jar/TurtleLauncher?style=for-the-badge)
![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android)
![Last Commit](https://img.shields.io/github/last-commit/Endiq-jar/TurtleLauncher?style=for-the-badge)

<H1 ALIGN= "CENTER">A modern, high-performance Android launcher for Minecraft: Java Edition.</H1>

Turtle Launcher is a fast, beautiful, and feature-rich Minecraft Java launcher built for Android by **Endiq**. Based on the open-source **Turtle Launcher** project, it combines a modern Material 3 interface with powerful customization, performance optimizations, and extensive modding support.

![Minecraft](1.jpg)
---

## ✨ Features

### 🎨 Modern UI

* Material Design 3
* Dynamic themes & accent colors
* Smooth animations
* Responsive phone & tablet layouts
* Dark mode
* Gaming-inspired interface

### 🚀 Performance

* Fast startup
* Low RAM usage
* Optimized rendering
* Hardware-accelerated UI
* Intelligent memory management
* Stable FPS & faster launches

### 🎮 Minecraft Support

* Release, Snapshot, Beta & Alpha
* Fabric
* Quilt
* Forge
* NeoForge

### 📦 Content Management

* Mods
* Resource Packs
* Shader Packs
* Datapacks
* Worlds
* Screenshots
* Logs

### 🛠 Launcher Tools

* Multiple instances
* Java Manager
* Microsoft, Offline, Battly & Ely.by accounts
* Download Manager
* Live launch & crash logs
* Customizable Discord Rich Presence with version templates, status, timer, and artwork
* Friends / LAN play (Terracotta over EasyTier) — host or join straight from the home screen
* Chooseable screen transitions — slide, bounce, fade, or zoom, applied app-wide
* Backup, import & export

### 🧩 ANGLE Support (PowerVR fix for MC 26.x)

* Runs games on **ANGLE** — MojoLauncher's [ANGLE plugin](https://github.com/MojoLauncher/AnglePlugin) when it is installed, otherwise the ANGLE copy built into the launcher — instead of the device's own GLES/EGL driver
* **Forced by default on PowerVR/Imagination GPUs** (Settings → Game → *Force ANGLE on PowerVR*), which is what lets those devices start **Minecraft 26.3**: its SDL3 windowing path never gets a GL context out of the PowerVR vendor driver
* ANGLE sits *under* whichever renderer is selected (GL → MobileGlues → ANGLE → Vulkan, or LTW over ANGLE), exactly like MojoLauncher's GLES provider model; renderers that ship their own driver (Zink, VirGL, VGPU, Freedreno) are left untouched
* One-tap install/update of the ANGLE plugin from Settings → Game (downloaded from the release above; the launcher's built-in ANGLE keeps working until then)
* Also applies automatically after a launch that ended without a GL context, and the crash screen offers a *Force ANGLE for this device* repair button

### 🔓 Shizuku Support (optional)

* One-tap permission granting (all-files access, notifications, battery exemption)
* Android 14+ phantom-process limit removed — fixes games being killed mid-session
* Game process priority boost
* Full system logcat for crash reports, not just the launcher's own lines
* Works with both Shizuku (ADB or root) and Sui

Shizuku is completely optional — Turtle Launcher works fine without it.

### ⚙ Customization

* Custom backgrounds
* UI scaling
* Animation controls
* Performance profiles
* JVM & RAM settings
* Dark Mode — a bundled resource pack that darkens every in-game menu, button and container. Toggle it from the in-game menu's **Debug** tab; the game auto-reloads resource packs (F3+T) so it applies right away.

---

## 🏗 Built With

* Kotlin & Java
* Jetpack Compose
* Material Design 3
* AndroidX
* Coroutines & Flow
* Coil
* Lottie
* LWJGL 3
* GL4ES
* Mesa3D
* ANGLE (MojoLauncher ANGLE plugin)
* OpenJDK

---

## 🎯 Goals

* Faster startup
* Lower memory usage
* Better battery efficiency
* Smooth gameplay
* Stable frame pacing
* Modern user experience

---

## 🗺 Roadmap

### Completed

- [X] Modern UI
- [x] Theme engine
- [x] Instance Manager
- [x] Ai intregation
- [x] Performance improvements
- [x] Mesa3D
- [x] LTW Renderer
- [x] MobileGlues Renderer
- [x] Cleanroom Integration
- [x] Terracotta Integration
- [x] mclo.gs Integration

### Current

- [ ] Cloud Sync
- [x] Skin & Cape Manager
- [x] Turtle Client integration
- [x] Replace OSMesa with a real EGL implementation
- [x] ANGLE plugin integration (forced on PowerVR — MC 26.3 support)
- [x] Shizuku Support
- [x] Beryllium Integration
- [x] Modpack Instance
- [x] Animations/Transtions
- [x] Emotes

---

## FAQs
## 1. Is Turtle Launcher Safe?
### -> Yes — Turtle Launcher is safe to use. The source code is fully open on GitHub under the GPL-3.0 license. As always, only download the APK from trusted sources, like the official GitHub releases page..
## 2. Is Turtle Launcher free?
### -> Yes! Turtle Launcher is completely free to use, with no ads and no premium paywall.
## 3. Does Turtle Launcher support Minecraft mods?
### -> Yes. Turtle Launcher supports Fabric, Quilt, Forge and NeoForge, and you can install mods directly through the built-in mod Explorer.
## Does it require a Microsoft account?
### No. Turtle Launcher supports Microsoft, offline, Battly and Ely.by accounts, so you can play the way you prefer.
---

## 🤝 Contributing

Contributions are welcome! Feel free to submit issues, feature requests, or pull requests.

---

## 📄 License

Licensed under **GPL-3.0**.

---

## ❤️ Credits

Built upon ideas and technologies from:

* TurtleLauncher
* HMCL
* Boardwalk
* OpenJDK
* LWJGL
* Mesa3D
* GL4ES

Special thanks to all open-source contributors who make Android Minecraft possible.

---

## ⚠ Disclaimer

Turtle Launcher is an independent open-source project and is **not affiliated with Mojang Studios or Microsoft**.

Minecraft is a trademark of Mojang Studios.

---

## ⭐ Support

If you enjoy Turtle Launcher:

* ⭐ Star the repository
* 🐞 Report bugs
* 💡 Suggest features
* 🤝 Contribute code
* 📢 Share the project

Every contribution helps make Turtle Launcher better.
