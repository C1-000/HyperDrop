<div align="center">

<img src="https://github.com/user-attachments/assets/e8a3df6b-6e67-485a-ae1c-018ac24e87d4" width="120" height="120" style="border-radius: 24px;" alt="HyperDrop Icon"/>

# HyperDrop

**System-level MOONDROP earphone control for HyperOS devices**

[![Platform](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)](https://android.com)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-blueviolet?style=flat-square)](https://github.com/LSPosed/LSPosed)
[![HyperOS](https://img.shields.io/badge/ROM-HyperOS%203+-orange?style=flat-square)](https://hyperos.mi.com)
[![DSH](https://img.shields.io/badge/powered_by-dsh-4D6BFE?style=flat-square&logo=deepseek&logoColor=white)](https://github.com/deepseek-ai/deepseek-harness)

**English** | **[简体中文](../../README.md)** | **[繁體中文](README_zh-TW.md)**

</div>

An Xposed module that provides system-level control for **MOONDROP earphones** on Xiaomi HyperOS devices. PUDDING / MD-TWS-056 is the first supported model, with more models planned.

### Earphone Features

- **Noise Cancellation Control** — Supports Off / Transparency / Basic ANC / Adaptive ANC / Wind Noise Reduction.
- **Three-part Battery Display** — Reads the real PUDDING GAIA battery levels for the left earbud, right earbud, and charging case.
- **Earbud-side Mode Sync** — When ANC is changed by a long-press on the earbuds, the module syncs it from an active report or the ~3 s status poll and refreshes all three battery values at the same time.
- **Gain Control** — Supports Low / Medium / High output gain.
- **Indicator Light Control** — Turns the earphone indicator light on or off.

### HyperOS Integration

- **Hyper Island** — Uses HyperOS Focus Notification to show earphone status and opens the earphone control popup when tapped.
- **Fusion Device Center** — Supports the HyperOS Device Center earphone card entry.
- **Settings Integration** — Integrates with the HyperOS advanced headset page and system Bluetooth device settings.
- **Model Compatibility** — Uses the internal compatibility Device ID `01010607`; no user-facing spoofed-model setting is required.

### Module Features

- **Quick Popup** — Tap the Hyper Island or an earphone entry to view battery, ANC, and quick controls.
- **Dedicated Detail Page** — Provides the PUDDING product image, battery, ANC, gain, indicator light, and app settings.
- **Languages** — Supports Traditional Chinese, Simplified Chinese, English, and Follow System; Follow System is the default on first install, falling back to English for unsupported locales.

### Device Support and Protocol

HyperDrop communicates directly with the earphones over Classic Bluetooth RFCOMM / SPP and controls supported MOONDROP models through Qualcomm GAIA v4. The MOONDROP official app does not need to stay running.

For protocol details, commands, battery parsing, and HyperOS mapping, see [PUDDING_ADAPTATION.md](../../PUDDING_ADAPTATION.md).

### System Requirements

- Xiaomi device running **HyperOS** (Android 15+; Hyper Island integration primarily targets HyperOS 3).
- **LSPosed** API version >= 101.

### Usage

1. Install the APK.
2. Enable the module in LSPosed and select the recommended scopes.
3. Restart the required scopes or reboot the device.
4. Connect a supported MOONDROP earphone (currently PUDDING / MD-TWS-056) via Bluetooth.

### Coexisting with the Official App (Optional)

The MOONDROP official app and HyperDrop both use the same SPP / GAIA control channel. To have HyperDrop yield the control channel while the official app is running, enable **Yield to official app** in HyperDrop settings:

1. Enable the toggle in HyperDrop settings (off by default).
2. Add the official app `com.moondroplab.moondrop.moondrop_app` to this module's LSPosed scope (included in the recommended scope list since 1.0.3).
3. Reboot the device so the new scope takes effect.

While enabled, once the official app process is alive and the earphones are connected, HyperDrop releases the RFCOMM channel, stops sending control commands, and hides its own status cards; control resumes automatically ~90 s after the official app is force-stopped. Behavior is unchanged when the app is not installed, the scope is not active, or the toggle is off.

### Credits

- [HyperPods](https://github.com/Art-Chen/HyperPods) — original project foundation.
- [OppoPods](https://github.com/1812z/OppoPods) — reference for HyperOS earphone integration and implementation.
- [HyperOriG](https://github.com/KiriChen-Wind/HyperOriG) — reference for multi-process HyperOS integration, Focus Island, and Device Center routing.
- [PuddingPods](https://github.com/lingbai-rong/PuddingPods) — foundation of this project.
- [Miuix](https://github.com/YuKongA/miuix) — HyperOS-style Compose UI components.

### License

[GPL-3.0](../../LICENSE)
