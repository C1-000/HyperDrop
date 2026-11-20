<div align="center">

<img src="https://github.com/user-attachments/assets/e8a3df6b-6e67-485a-ae1c-018ac24e87d4" width="120" height="120" style="border-radius: 24px;" alt="HyperDrop Icon"/>

# HyperDrop

**为 HyperOS 设备提供系统级 MOONDROP 水月雨耳机控制**

[![Platform](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)](https://android.com)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-blueviolet?style=flat-square)](https://github.com/LSPosed/LSPosed)
[![HyperOS](https://img.shields.io/badge/ROM-HyperOS%203+-orange?style=flat-square)](https://hyperos.mi.com)
[![DSH](https://img.shields.io/badge/powered_by-dsh-4D6BFE?style=flat-square&logo=deepseek&logoColor=white)](https://github.com/deepseek-ai/deepseek-harness)

**[English](docs/readme/README_en.md)** | **简体中文** | **[繁體中文](docs/readme/README_zh-TW.md)**

</div>

为小米 HyperOS 设备提供系统级 **MOONDROP 水月雨系列耳机** 控制的 Xposed 模块；内置 **45 款机型**素材与适配档案，其中 **PUDDING / MD-TWS-056 与 MIRAGE 已经实机验证**，其余机型按协议推断适配。

### 耳机功能

> 具体显示的控制项以当前机型的适配能力为准；未验证机型仅显示其支持的保守能力。

- **降噪控制** — 支持关闭 / 通透 / 基础降噪 / 自适应降噪 / 抗风噪。
- **三路电量** — 直接读取左耳、右耳和充电盒的 GAIA 电量。
- **耳机端模式同步** — 耳机长按切换 ANC 后，通过主动回报或状态轮询同步模式，并一并刷新三路电量。
- **增益控制** — 支持低 / 中 / 高三档输出增益。
- **指示灯控制** — 支持耳机指示灯开关。
- **提示音** — 支持提示音开关与音量调节（松手后上报，右侧显示百分比）。
- **LHDC** — 支持 LHDC 编码开关（开启时可能短暂断开并自动重连）。
- **双设备连接** — 支持双设备连接开关；与 LHDC 互斥，切换时会弹出确认提示。
- **状态刷新** — 控制页右上角刷新按钮，立即向设备查询并回显真实状态。

### HyperOS 集成

- **超级岛** — 通过 HyperOS Focus Notification 显示耳机状态，点击进入耳机控制弹窗。
- **融合设备中心** — 支持 HyperOS 设备中心耳机卡片入口。
- **系统设置集成** — 接入 HyperOS 高级耳机页面与系统蓝牙设备设置。
- **型号兼容** — 内部固定使用兼容 Device ID `01010607`，无需用户手动设置伪装型号。

### 模块功能

- **快捷弹窗** — 点击超级岛或耳机入口，显示电量、降噪和快捷控制。
- **独立详情页** — 提供已适配机型的产品图、电量、ANC、增益、指示灯、提示音、LHDC、双连、头部追踪和应用设置。
- **内置机型素材** — 45 款机型的整机 / 左右耳图片内置于安装包，未匹配的机型回退到默认素材；同时保留自定义图片覆盖能力（设置页可上传自己的整机 / 左右耳图）。
- **适配情况页** — 在设置中查看全部已适配机型，按「实机验证」和「协议推断」分组。
- **多语言** — 支持繁體中文、简体中文、English 和跟随系统；首次安装默认跟随系统，未命中受支持语言时回退 English。

### 设备支持与协议

HyperDrop 直接通过 Classic Bluetooth RFCOMM / SPP 与耳机通信，并使用 Qualcomm GAIA v4 控制受支持的 MOONDROP 耳机；不依赖水月雨官方 App 常驻。已适配机型见设置中的「设备适配情况」页面；协议、命令、电量解析和 HyperOS 映射见 [PUDDING_ADAPTATION.md](PUDDING_ADAPTATION.md)。

其中 PUDDING / MD-TWS-056 与 MIRAGE 为实机验证；其余机型依据 miuixmoondrop 的适配资料按协议推断，命令与状态回显未逐台真机确认。

### 系统要求

- 小米设备，运行 **HyperOS**（Android 15+；超级岛相关功能以 HyperOS 3+ 为主要适配目标）。
- **LSPosed** API 版本 >= 101。

### 使用

1. 安装 APK。
2. 在 LSPosed 中启用模块并勾选推荐作用域。
3. 重启所需作用域或直接重启手机。
4. 通过蓝牙连接受支持的 MOONDROP 耳机（45 款内置机型，其中 PUDDING / MD-TWS-056 与 MIRAGE 已实机验证）。

### 与官方 App 共存

水月雨官方 App 与 HyperDrop 会争用同一 SPP / GAIA 控制通道。若希望官方 App 运行时由 HyperDrop 主动让出控制通道，可在「应用设置」中开启 **官方 App 运行时退避**：

1. 在 HyperDrop 设置中开启该开关。
2. 在 LSPosed 中将官方 App `com.moondroplab.moondrop.moondrop_app` 加入本模块作用域。
3. 重启手机使新作用域生效。

开启后：官方 App 进程存活且耳机已连接时，HyperDrop 会释放 RFCOMM 通道、停止下发控制命令并隐藏本模块的状态卡片；强制停止官方 App 后约 12 秒自动恢复控制。

### 致谢

- [HyperPods](https://github.com/Art-Chen/HyperPods) — 原始项目基础。
- [OppoPods](https://github.com/1812z/OppoPods) — HyperOS 耳机集成与实现参考。
- [HyperOriG](https://github.com/KiriChen-Wind/HyperOriG) — HyperOS 多进程接入、Focus Island 与设备卡路线参考。
- [PuddingPods](https://github.com/lingbai-rong/PuddingPods) — 本项目基础。
- [Miuix](https://github.com/YuKongA/miuix) — HyperOS 风格 Compose UI 组件。
- [MiuixMoondrop](https://github.com/huime180/MiuixMoondrop) — 多设备适配资料、能力定义与互斥逻辑参考。

### 支持

如果这个项目对你有帮助请考虑支持，这会帮助我更好更快地开发，感激不尽
<img width="3026" height="2160" alt="fbe8b85f-33cd-45c3-8e8f-af21bfeea7f7" src="https://github.com/user-attachments/assets/ac10ea9f-9173-4f14-9fe2-40762ba41877" />

### 许可证

[GPL-3.0](LICENSE)
