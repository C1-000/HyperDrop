<div align="center">

<img src="https://github.com/user-attachments/assets/e8a3df6b-6e67-485a-ae1c-018ac24e87d4" width="120" height="120" style="border-radius: 24px;" alt="HyperDrop Icon"/>

# HyperDrop

**為 HyperOS 裝置提供系統級 MOONDROP 水月雨耳機控制**

[![Platform](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)](https://android.com)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-blueviolet?style=flat-square)](https://github.com/LSPosed/LSPosed)
[![HyperOS](https://img.shields.io/badge/ROM-HyperOS%203+-orange?style=flat-square)](https://hyperos.mi.com)
[![DSH](https://img.shields.io/badge/powered_by-dsh-4D6BFE?style=flat-square&logo=deepseek&logoColor=white)](https://github.com/deepseek-ai/deepseek-harness)

**[English](README_en.md)** | **[简体中文](../../README.md)** | **繁體中文**

</div>

為小米 HyperOS 裝置提供系統級 **MOONDROP 水月雨系列耳機** 控制的 Xposed 模組；內建 **45 款機型**素材與適配檔案，其中 **PUDDING / MD-TWS-056 與 MIRAGE 已經實機驗證**，其餘機型按協定推斷適配。

### 耳機功能

> 實際顯示的控制項以當前機型的適配能力為準；未驗證機型僅顯示其支援的保守能力。

- **降噪控制** — 支援關閉 / 通透 / 基礎降噪 / 自適應降噪 / 抗風噪。
- **三路電量** — 直接讀取左耳、右耳和充電盒的 GAIA 電量。
- **耳機端模式同步** — 耳機長按切換 ANC 後，透過主動回報或狀態輪詢同步模式，並一併刷新三路電量。
- **增益控制** — 支援低 / 中 / 高三檔輸出增益。
- **指示燈控制** — 支援耳機指示燈開關。
- **提示音** — 支援提示音開關與音量調節（鬆手後上報，右側顯示百分比）。
- **LHDC** — 支援 LHDC 編碼開關（開啟時可能短暫斷開並自動重連）。
- **雙裝置連線** — 支援雙裝置連線開關；與 LHDC 互斥，切換時會彈出確認提示。
- **狀態重新整理** — 控制頁右上角重新整理按鈕，立即向裝置查詢並回顯真實狀態。

### HyperOS 整合

- **超級島** — 透過 HyperOS Focus Notification 顯示耳機狀態，點擊進入耳機控制彈窗。
- **融合裝置中心** — 支援 HyperOS 裝置中心耳機卡片入口。
- **系統設定整合** — 接入 HyperOS 高級耳機頁面與系統藍牙裝置設定。
- **型號相容** — 內部固定使用相容 Device ID `01010607`，無需使用者手動設定偽裝型號。

### 模組功能

- **快捷彈窗** — 點擊超級島或耳機入口，顯示電量、降噪和快捷控制。
- **獨立詳情頁** — 提供已適配機型的產品圖、電量、ANC、增益、指示燈、提示音、LHDC、雙連、頭部追蹤和應用程式設定。
- **內建機型素材** — 45 款機型的整機 / 左右耳圖片內建於安裝包，未匹配的機型回退到預設素材；同時保留自訂圖片覆蓋能力（設定頁可上傳自己的整機 / 左右耳圖）。
- **適配情況頁** — 在設定中查看全部已適配機型，按「實機驗證」和「協定推斷」分組。
- **多語言** — 支援繁體中文、简体中文、English 和跟隨系統；首次安裝預設跟隨系統，未命中受支援語言時回退 English。

### 裝置支援與協定

HyperDrop 直接透過 Classic Bluetooth RFCOMM / SPP 與耳機通訊，並使用 Qualcomm GAIA v4 控制受支援的 MOONDROP 耳機；不依賴水月雨官方 App 常駐。已適配機型見設定中的「裝置適配情況」頁面；協定、命令、電量解析和 HyperOS 映射請見 [PUDDING_ADAPTATION.md](../../PUDDING_ADAPTATION.md)。

其中 PUDDING / MD-TWS-056 與 MIRAGE 為實機驗證；其餘機型依據 miuixmoondrop 的適配資料按協定推斷，命令與狀態回顯未逐台真機確認。

### 系統需求

- 小米裝置，執行 **HyperOS**（Android 15+；超級島相關功能以 HyperOS 3 為主要適配目標）。
- **LSPosed** API 版本 >= 101。

### 使用

1. 安裝 APK。
2. 在 LSPosed 中啟用模組並勾選建議作用域。
3. 重新啟動所需作用域或直接重新啟動手機。
4. 透過藍牙連線受支援的 MOONDROP 耳機（45 款內建機型，其中 PUDDING / MD-TWS-056 與 MIRAGE 已實機驗證）。

### 與官方 App 共存（可選）

水月雨官方 App 與 HyperDrop 會爭用同一 SPP / GAIA 控制通道。若希望官方 App 執行時由 HyperDrop 主動讓出控制通道，可在「應用程式設定」中開啟 **官方 App 執行時退避**：

1. 在 HyperDrop 設定中開啟該開關（預設關閉）。
2. 在 LSPosed 中將官方 App `com.moondroplab.moondrop.moondrop_app` 加入本模組作用域（自 1.0.3 起已加入建議作用域清單）。
3. 重新啟動手機使新作用域生效。

開啟後：官方 App 程序存活且耳機已連線時，HyperDrop 會釋放 RFCOMM 通道、停止下發控制命令並隱藏本模組的狀態卡片；強制停止官方 App 後約 12 秒自動恢復控制。未安裝官方 App、作用域未生效或開關關閉時行為不變。

### 致謝

- [HyperPods](https://github.com/Art-Chen/HyperPods) — 原始專案基礎。
- [OppoPods](https://github.com/1812z/OppoPods) — HyperOS 耳機整合與實作參考。
- [HyperOriG](https://github.com/KiriChen-Wind/HyperOriG) — HyperOS 多程序接入、Focus Island 與裝置卡路線參考。
- [PuddingPods](https://github.com/lingbai-rong/PuddingPods) — 本專案基礎。
- [Miuix](https://github.com/YuKongA/miuix) — HyperOS 風格 Compose UI 元件。
- [MiuixMoondrop](https://github.com/huime180/MiuixMoondrop) — 多裝置適配資料、能力定義與互斥邏輯參考。

### 授權條款

[GPL-3.0](../../LICENSE)
