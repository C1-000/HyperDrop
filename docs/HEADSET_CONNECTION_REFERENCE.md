# 耳机连接参考文档（FxxkMoondrop / MoonEar）

> 本文聚焦"与耳机的连接"这一主题，整理开源项目 [bqj6666/FxxkMoondrop](https://github.com/bqj6666/FxxkMoondrop)（README 中称 **MoonEar**，v3.4.2 / versionCode 402，Kotlin，GPL-3.0）的连接、识别、能力探测与机型控制方案，作为 HyperDrop 后续适配更多机型（尤其 D4：BLE 传输）的参考依据。
>
> 逆向与协议内容仅供学习研究。MoonEar 自述为 "AI vibe coding" 项目，其 9ECA、Fast Pair 注入等部分尚无真机验证，本文对其结论标注证据来源，落地前需实测确认。

## 1. 信息来源

- 仓库：https://github.com/bqj6666/FxxkMoondrop ，默认分支 `main`，301 文件，约 5.2 MB，44 stars / 5 forks，最近 push 2026-11-19。
- 包结构：包名 `com.fxxkmoondrop.secret`，源码位于仓库根 `src/`（Gradle 中 `kotlin.directories.add("../src")`），非 `app/src/main/java`。
- 构建栈：AGP 9.4.1 / Kotlin 2.4.10 / Gradle 9.6.0；compileSdk 37 / minSdk 32 / targetSdk 37；libxposed api 102.0.0（compileOnly）；miuix-ui-android + miuix-preference-android 0.9.4；Compose BOM 2026.05.01；org.luckypray:dexkit 2.3.0。
- 本地已下载的资料快照：`_fm/`（`ADAPTATION.md`、`ARCHITECTURE.md`、`DEVELOPMENT.md`、`README.md`、`CHANGELOG.md`、`CapabilityProbe.kt`、`AncProfileLib.kt`、`GaiaCommands.kt`、`GaiaConstants.kt`、`GaiaPacketHandler.kt`、`GaiaRfcommFramer.kt`、`DeviceMatcher.kt`、`DeviceFeatures.kt`、`GattOperationQueue.kt` 等）。
- 核心连接模块：`GaiaBleClient.kt`（约 96 KB，BLE GATT / RFCOMM 直连单例 + 三协议自动识别）、`CapabilityProbe.kt`（ANC 路径探测状态机）、`GattOperationQueue.kt`（GATT 读写串行化）、`BleSourceProtocol.java` / `BleSourceSwitchClient.java`（9ECA）。

## 2. 整体架构

MoonEar 是**双形态**模块：

- **App 进程**（`com.fxxkmoondrop.secret`）：UI、设备发现、连接管理、档案数据。
- **GMS 进程 Hook 注入**：借道 `com.google.android.gms` 完成 LE 扫描（见 §4.2）与 Fast Pair 弹窗/Hearable Controls 注入。

LSPosed 作用域仅 `com.google.android.gms` 与 `com.android.settings`；`com.android.bluetooth`、`com.moondroplab.moondrop.moondrop_app` 虽有代码但**不在作用域，不会执行**。跨进程通信用 Broadcast + `PrefsProvider`（ContentProvider 共享 SharedPreferences `"cfg"`），3.3.1 起用 Binder UID + 自定义权限 `com.fxxkmoondrop.secret.permission.HOOK_IPC` 鉴权。清单仅 6 项权限，**不声明** SYSTEM_ALERT_WINDOW / FOREGROUND_SERVICE；**BLE GATT 直连不需要 Root**。

## 3. 三协议自动识别

| 协议 | 承载 | 服务/UUID | 典型机型 |
|---|---|---|---|
| GAIA V3 | BLE GATT | `00001100-d102-11e1-9b23-00025b00a5a5` | GA2、EDGE 等 dual-mode |
| GAIA V4 | RFCOMM / SPP | `00001101-0000-1000-8000-00805f9b34fb` | 布丁 PUDDING |
| 私有 9ECA | BLE GATT | `9eca0000-7f3a-4f32-9a38-a91b2c6e0100` | 蓝讯系（音量/EQ/音源） |

关键结论（ADAPTATION.md）：**chipType 只决定 BLE 封装与 OTA 通道，不决定控制协议**。例如 Airoha 的 GAIA over LE 加 `FF 01 00 00` 头，Jieli 的私有栈只做 OTA，而 Pudding/Mirage 官方标 jieli 却跑标准 GAIA——所以不能按 chipType 推断控制协议，必须靠探测。

GAIA V3 包格式与 HyperDrop 一致：`[vendor 2B BE = 0x001D][commandValue 2B BE][payload]`，`commandValue = (feature << 9) | (type << 7) | command`，type 0=COMMAND / 1=NOTIFICATION / 2=RESPONSE。

## 4. 连接流程（本文重点）

### 4.1 通用流程

1. 系统配对得到设备；`HeadsetDetectService` 后台轮询已连接设备。
2. `DeviceMatcher` 门禁判定是否为支持的水月雨设备（见 §6）。
3. 协议探测：按设备特征尝试 GATT / SPP / 9ECA，成功者建立长连接。
4. `CapabilityProbe` 探测能力位与 ANC 路径（见 §5）。
5. `GattOperationQueue` 串行化后续所有 GATT 读写。

### 4.2 双地址问题与动态 LE 地址发现（最值得借鉴）

GA2 等 dual-mode 设备有**两个地址**：系统配对/A2DP 用的 PUBLIC 地址，与 BLE GATT 必须的 LE 随机地址。若拿 bonded PUBLIC 地址去连 GATT，会**空等约 50 秒后超时**。MoonEar 的方案是零硬编码动态发现：

```
无缓存 → 发 ACTION_REQ_LE_SCAN 广播
       → GMS 进程 Hook 调 BluetoothLeScanner 扫描
         （30 秒节流，按名称含 moondrop 过滤）
       → ACTION_LE_ADDR_FOUND 回传 LE 地址
       → 持久化到 "cfg" → GATT 连接 → 写回缓存，下次秒连
```

`CACHED_LE_MAX_TRIES = 3`：每次 5 秒轮询，约 15 秒连不上就淘汰该缓存地址重新发现。经典蓝牙设备（如布丁）BLE 失败后**回退 RFCOMM/SPP**；3.4.2 修复过"该回退在重构中丢失调用点导致空等 50 秒"的回归。

> 对 HyperDrop 的意义：这正是审计 D4 的核心。当前 `DeviceSession.kt` 硬编码 `createRfcommSocketToServiceRecord`，`PodTransport` 三值只赋值不读取，无 `BluetoothGatt` / `BluetoothLeScanner`。要实现 EDGE 等 dual-mode 机型，必须补上动态 LE 地址发现 + GATT 通道。（本轮不实现。）

### 4.3 GATT 读写串行化

`GattOperationQueue` 保证同一时刻只有一条读/写在途，每个请求带归属标记与截止时间，会话退役时标记使陈旧回调失效；分片、畸形、迟到回复在 framer 层（`GaiaRfcommFramer.kt`）校验丢弃。这是高频 `*_CHANGED` 回包场景下避免状态错乱的关键工程实践。

## 5. 能力探测（CapabilityProbe）

### 5.1 GET_SUPPORTED_FEATURES 的两种封装

MoonEar 用 `GaiaCommands.isFeaturePairList()` 按**载荷长度奇偶**区分：

- **奇数长度 = 特征对列表**（新固件）：`byte0 = hasMoreData`，其后每 2 字节 `(featureId, version)`。
- **偶数长度 = 32 位位图**（老固件/GA2）：每 4 字节一个大端 word，`feature ∈ word`，`bit = feature % 32`，`features.add(wordIdx * 32 + bit)`。

布丁实测 23 字节：`00 | 00 02 | 01 01 | 05 01 | 0D 01 | 0E 01 | 0F 01 | 10 01 | 13 01 | 14 01 | 16 01 | 20 01` → BASIC / 电量 0x0D / 增益 0x0F / LED 0x13 / ANC_V2 0x20，**无 AudioCuration、无空间音频**。MoonEar 曾因只认位图，把这 23 字节判为"截断"弃用，导致 `ancPath` 恒为 UNKNOWN、ANC 点不动（其 issue #4）——这与 HyperDrop 审计 D1 的结论一致，但 MoonEar 额外覆盖了位图分支。

> 与 HyperDrop 对照：HyperDrop 在 beta2 修 D1 后只实现"特征对列表"分支（`GenericGaiaCommandTable.kt`：`hasMore = payload[4] == 1`，`i` 从 5 每 2 字节取一个 featureId）。若遇到老固件 GA2 的位图封装会解析错误。是否补位图分支需实测确认（HyperDrop 唯一受控机型布丁恰好是特征对列表）。

### 5.2 三重探测机制

MoonEar 不只依赖静态能力表：

1. **能力表查询**：`FEATURE_BASIC(0x00)` 的 `CMD_GET_SUPPORTED_FEATURES(1)` + `CMD_GET_VARIANT(4)`，`MAX_FEATURE_PAGES = 128`（GAIA feature id 是 7 位）。
2. **主动阶梯探测**：对 AudioCuration 与 ANC_V2 主动发 `GET_MODE` / `GET_SWITCH_CONF`，靠真实回包确认路径，`PROBE_DELAY_BASE = 400L`、`PROBE_DELAY_STEP = 250L`、`PROBE_TIMEOUT_MS = 3500L`。
3. **响应驱动补全** `onFeatureResponseSeen(feature)`：任何 feature 只要收到成功回包就记入 `features`，无需先在能力表里声明。
4. `reset()` 在断开/重连/设备切换时清空全部探测态（历史 bug #2：探测标志不重置导致 `ancPath` 卡在 -1）。
5. `START_THROTTLE_MS = 8000L` 防重连风暴叠加探测。

`ancPathFrom(features)` 优先级：**AudioCuration > ANC_V2 > ANC_V1 > UNKNOWN**。

> 对 HyperDrop 的意义：HyperDrop 的能力补全是"档案声明（出厂上限）+ 观测补全（只开不关）"，观测源是 `GET_SUPPORTED_FEATURES` 和各控制回包，但没有"主动阶梯探测 ANC 路径"。若未来遇到能力表缺失的新机型，可借鉴主动探测。

### 5.3 HyperDrop 的探测日志现状

当前 HyperDrop 的能力探测**过程结果会写入日志**：

- `DeviceSession.kt` 收到 `GaiaReport.SupportedFeatures` 时：`Log.i(TAG, "supported features: ${report.features} hasMore=${report.hasMore}")`
- 每个 GAIA 回包：`Log.v(TAG, "GAIA payload: ${payload.toHexString(HexFormat.UpperCase)}")`
- 开启 RFCOMM 调试时另有 `RfcommLog` 记录收发原始字节。

结果同时写入**内存**缓存 `DeviceRegistry.observedFeatures`（按 `DeviceModels.normalize(name)` 作 key），供 UI 与会话读取；**不持久化到磁盘，进程重启即清空**。这是排障时判断"设备到底上报了哪些能力"的第一手依据。

## 6. 设备识别门禁（本轮不改，仅记录）

MoonEar `DeviceMatcher.kt` 三级判定：

1. 型号关键字白名单 `KNOWN_KEYS` = moondrop / golden ages / goldenages / 水月雨 / robin / 知更鸟（小写 contains）。
2. `AncProfileLib.isMoondrop(name)` 型号档案判定。
3. **协议指纹学习**：把曾由 GAIA/9ECA 服务发现确认过的设备名持久化到 `SharedPreferences "cfg"` 的 `learned_device_names` / `rejected_device_names`。`allowProbe(name)` 允许对名字不含品牌词的已连接设备发起一次指纹探测，通过则 `learn()` 永久放行，失败 `reject()` 拉黑；`clearRejected()` 供"刷新状态"按钮重试（issue #12：静默拉黑用户看不出来）。

这解决了 "Robin's Earphones" 这类显示名不含 "moondrop" 的机型被名字门禁拦死的问题。HyperDrop 当前仍用纯别名匹配（`DeviceModels.match()`：最长别名 contains → 名字含 MOONDROP/水月雨走 fallback → 否则 null）；本轮按用户要求**不改识别算法**，仅把 robin 的别名补上 "robin's earphones"。协议指纹学习作为后续候选方案记录于此。

## 7. 七款真机验证机型的能力与控制方案

来源：MoonEar README L99–105（实机验证清单）+ `ADAPTATION.md` + `AncProfileLib.kt` 的 `PROFILES` / `ANC_V2_PROFILES` / `DC_PROFILES`。

| 机型 | 连接 | ANC 路径与映射 | 其它能力 | 备注 |
|---|---|---|---|---|
| 梦回2 / Golden Ages 2 (GA2) | GAIA over GATT | AudioCuration 4 档，SET `[1,2,4,3]` / GET `[0,1,2,3]` | **有空间音频**，三档增益 `gainMap=[2,1,0]`，无 LED，无充电盒电量 | `PROFILES` + `DC_PROFILES` |
| 梦回1979 / Golden Ages | GAIA | 与 GA2 同平台/主控 | 同上 | 验证通过 |
| 太空漫游2 / Space Travel 2 | GAIA over GATT（BT8932F） | AudioCuration 4 档 `[1,2,4,3]` / `[0,1,2,3]` | 三档增益 `[2,1,0]`，无空间/LED | 涉及 9ECA 私有协议 |
| 太空漫游2 ULTRA | 国产 SoC | 验证通过，协议未公开 | — | — |
| U.C.T.S (MD-OWS-014) | GAIA | 开放式耳机，**硬件无降噪** | 左右耳电量正常 | 无 ANC 能力 |
| 布丁 PUDDING (MD-TWS-056) | GAIA V4 / RFCOMM | ANC_V2 5 档：关0/自适应1/通透2/抗风3/基础4；GET 方向 `[0,4,2,3,1]` | 三路电量、三档增益 `[2,1,0]`、指示灯、LHDC | `ANC_V2_PROFILES` + `DC_PROFILES`；`gainFeature=0x1E`、`ledFeature=0x26` |
| 猫咖 MOCA | 疑似蓝讯 BT5.4/LC3 | — | 三档增益 + 指示灯 | `DC_PROFILES` |

**PUDDING ANC 细节**：SET `00 1D 40 04 <mode>`，RX `00 1D 41 04 <mode>`；mode 关=0x00 / 自适应=0x01 / 通透=0x02 / 抗风=0x03 / 基础降噪=0x04。UI 顺序为 关/降噪/通透/抗风/自适应 (`ANC_UI_ORDER = [1,0,2,4,5,3]`)。这与 HyperDrop `PuddingCommandTable` 的 wire 值完全一致（HyperDrop 的 UI status 编号 1..5 与之不同，但映射表已对齐）。

**其它已归档但为"理论支持"的机型**：ALICE (QCC5151)、SPARKS (QCC3040)、VOYAGER (QCC5144)、NEKOCAKE (BT8922E)、PILL (BT8932F)、ULTRASONIC (BT8952F)、Robin (BT8952F)、太空漫游一代、方糖 BLOCK、羽翼 EDGE·EDGE2。

> HyperDrop 现已把上表 7 款 MoonEar 实机验证机型的能力与控制映射加入档案并标 `verified=true`（其中 PUDDING 与 MIRAGE 共享同一档案；MIRAGE 为布丁联动版），共 8 款 verified。其余 37 款仍为协议推断。建议后续用真机日志逐款核对增益/LED/ANC 细节。

## 8. Codec（编码）控制方案

`GaiaCommands.kt` 的 codec feature 为 `F_CODEC_TYPE = 0x10`：

| 编码 | GET command | SET command | 说明 |
|---|---|---|---|
| LC3 | 1 (`C_CODEC_GET_LC3_STATE`) | 3 (`C_CODEC_SET_LC3_STATE`) | `codecGetLc3()` / `codecSetLc3(state)` |
| LDAC | 2 | 4 | 封装尚未接入 UI |
| LHDC | 5 (`C_CODEC_GET_LHDC_STATE`) | 6 (`C_CODEC_SET_LHDC_STATE`) | `codecGetLhdc()` / `codecSetLhdc(state)` |

`codecSet(codec, on) = v3Packet(0x10, codec, byteArrayOf(if (on) 1 else 0))`。这印证了 HyperDrop 现有 LHDC 命令字节 `00 1D 20 05`（GET）/ `00 1D 20 06`（SET）正确；LC3 为 GET `00 1D 20 01` / SET `00 1D 20 03`，本轮据此在 HyperDrop 后端补齐 LC3 能力位、命令与控件。

`DeviceFeatures.kt`（官方 App r14 反编译）feature id 全表节选：0 基础 / 1 耳机 / 2 降噪 / 8 音频定制 / 9 佩戴 / 13 电量 / 15 增益 / 16 编解码器 / 18 空间音频 / 19 指示灯 / 20 双连 / 24 电源控制 / 32 降噪V2 / 33 降噪V3。这也印证了 HyperDrop 在 beta2 把 `F_SPATIAL_AUDIO` 从 24 修正为 18（0x12）是正确的。

## 9. 其它 GAIA 命令与工程实践

- **ANC 三路径**（`GaiaCommands.kt`）：ANC_V1 feature=2，GET_STATE cmd=1 / SET_STATE=2 / GET_CURRENT_MODE=4 / SET_MODE=5；AudioCuration feature=8，GET_MODE cmd=3 / SET_MODE=4 / 切换配置 41/42；ANC_V2 feature=32，GET_CURRENT_MODE cmd=3 / SET_CURRENT_MODE cmd=4。
  - 注意：MoonEar 的 ANC_V1 实际控制用 GET_STATE=1/SET_STATE=2；HyperDrop 审计 D2 后改用 GET/SET_CURRENT_MODE=4/5。两者取值不同，双方均无 ANC_V1 真机验证，需在实机上核对。
- **空间音频 / 头部追踪**：feature 18，state GET/SET=1/2，head-tracking GET/SET=3/4；`headTrackingOn(mode) = mode == 2`。MoonEar 复用系统官方两行（放开 `Spatializer.isAvailableForDevice` / `hasHeadTracker` 判定并转发 GAIA），而非自绘。
- **电量**：GAIA `TX: 00 1D 1A 01 01 02`（左+右），Battery ID 1=左 / 2=右 / 3=盒，回包 `00 1D 1B ...`；单次回包缺某组件时保留最近有效值（HyperDrop 亦采用此策略）。
- **Fast Pair / Settings 注入**：Hook GMS `HalfSheetActivity` 弹窗、`com.android.settings` 蓝牙详情页、GMS Hearable Controls（GFPS 消息组 0x08），把官方面板点击转成 GAIA，并把真实档位注入官方 DataStore；混淆类名用 DexKit 特征定位兜底。全部注入点按"是否支持的耳机"门禁，非目标设备零介入。
- **测试实践**：`app/src/test/java/com/fxxkmoondrop/secret/` 有约 40 个测试文件（`GaiaCommandsTest`、`GaiaReceiveValidationTest`、`GattOperationQueueTest`、`BleSourceReceiveTest`、`AncProfileLibTest`、`ProbeBudgetTest` 等）。

## 10. 对 HyperDrop 的可借鉴清单（按优先级）

1. **【高，D4】动态 LE 地址发现 + GATT 通道 + BLE 失败回退 SPP**：适配 EDGE 等 dual-mode 机型的前置，需借道 GMS 进程扫描（本轮不实现）。
2. **【高】能力表双封装判别**：补 32 位位图分支，避免老固件解析错误（需实测样本）。
3. **【中】主动阶梯探测 ANC 路径 + 响应驱动补全**：新机型能力表缺失时更稳健。
4. **【中】协议指纹学习**：补名字门禁盲区（Robin's Earphones 等），本轮不改识别算法，仅记录。
5. **【中】`GattOperationQueue` 串行化 + framer 层畸形包过滤**：高频回包下状态一致性。
6. **【低】9ECA 私有协议**：音量/EQ/音源控制，MoonEar 亦未真机验证，暂不纳入。

## 11. 待核对的命令冲突

| 项 | MoonEar | HyperDrop（当前） | 状态 |
|---|---|---|---|
| ANC_V1 GET/SET | `1 / 2`（GET/SET_STATE） | `4 / 5`（GET/SET_CURRENT_MODE，D2 修复后） | 双方无真机验证；待实测 |
| LHDC GET/SET | `5 / 6` | `5 / 6` | ✅ 一致 |
| LC3 GET/SET | `1 / 3` | 本轮补 `1 / 3` | ✅ 采纳 |
| 空间音频 feature | `0x12 (18)` | `18`（beta2 修正后） | ✅ 一致 |
| PUDDING gain/LED feature | `0x1E / 0x26` | `0x1F / 0x27` | 其自述与自身能力表矛盾，暂不改 HyperDrop |
