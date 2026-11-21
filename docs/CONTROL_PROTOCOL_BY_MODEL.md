# 各机型真实控制协议结论 —— 官方 app smali 一级证据

> 回答的问题：在官方 app 中，**每款蓝牙 TWS 耳机日常真正在蓝牙上跑的控制协议是什么**？
> 证据：对 `pudding\_work\ml_smali`（11,398 个 smali class，来自官方 APK `com.moondroplab.moondrop.moondrop_app`）的逐类反编译阅读。

## 0. 一句话结论

**在这份官方 app 里，所有蓝牙 TWS 耳机的日常控制（ANC、EQ、电量、设备信息、提示音、空间音频、灯光……）统一走高通 GAIA 协议（`com.qualcomm.qti.gaiaclient`），与芯片平台无关；`chipType` 不选择控制协议，只配置 GAIA 的 BLE 包封装。私有协议栈只用于 OTA（Airoha/Jieli）与音源切换等特定功能（Bluetrum 9ECA），且 9ECA 也不含 ANC。**

即：**"控制统一 GAIA + OTA 切私有 + 9ECA 补特定功能"** 是这套 app 的真实架构。Bluetrum 9ECA 额外承担日常的音源/EQ/MIC/音量控制，但不含 ANC。

---

## 1. 证据等级

| 标记 | 含义 |
|---|---|
| 【一级】 | smali 字节码直接证明：具体方法名 + 行号 + const-string / 调用指令 |
| 【推断】 | 基于一级证据的合理外推，未逐字节验证 |
| 【二手】 | MiuixMoondrop / FxxkMoondrop / PuddingPods 等第三方实测记录，仅对照 |

运行时日志 `_logs2\` 中有 GAIA 会话的受控设备只有 MOONDROP Pudding（`3B:75:4A:DB:15:40`，无 HCI snoop / btsnoop / pcap 抓包文件）；日志里另一个 MAC `8C:51:09:BE:A1:1C`（Mijia Audio Glasses 2 A11C）只出现在被动系统路由列表中，从未成为受控目标。因此"每款机型"的结论只能从 app 的分发逻辑推出，不能靠日志。

---

## 2. 架构总纲的四条一级证据链

### 2.1 连接层：`connectDeviceBT` 根本不按 chipType 分支【一级】

`native\handlers\BluetoothConnectionHandler.smali`：

- `handleConnectDeviceBT`(L1683) → `handleConnectDeviceBT$lambda$0`(L1724–2311)
- L1969 `resolveBluetoothType(String,String)` → L1977 `new Device(name,address,bluetoothType)` → **L2036 `connectionRepository.connect(context,device)`**
- 这个 `connectionRepository` 来自高通 `GaiaClientApplication`（`FlutterNative.smali` 初始化处无条件 `GaiaClientApplication.onCreate()`，L280–288）。
- 唯一分支：`transportType`（`resolveBluetoothType` L5706）只有 `LOW_ENERGY`→BLE GATT（L5852）与 `CLASSIC`→RFCOMM SPP（L5860）二选一，**没有厂商/chipType 维度**。CLASSIC 时申请的前台服务标签 `qualcomm_rfcomm`（L2018）只是 transport 名。
- 关键容错的日志串（`_re\libapp.strings.txt`）：L7408 `connectDeviceBT: resolve chip type failed:`、L7410 **`connectDeviceBT: setChipType failed, continue connecting.`** —— chipType 设置失败也照样连 GAIA，进一步证明它不是连接的必要条件。

→ **结论**：所有 `connectDeviceBT` 都进入 GAIA 的 `ConnectionRepository.connect()`。Dart 层 `resolveChipTypeForConnection(uuid)`（`_re\libapp.strings.txt` L43472、L7456）解析出的 chipType 字符串，通过 `handleSetChipType`(L5200) 写入 `LeGattChipConfig.INSTANCE.setChipType(String)`(L5220–5224)，仅此而已。

### 2.2 控制层：ANC / EQ / 电量全部收敛到 GAIA【一级】

| 功能 | handler | GAIA 落点（证据） |
|---|---|---|
| ANC V1 / AudioCuration | `AncHandler.smali` | `handleGetANCAction`(L232)/`handleGetANCMode`(L279) → `GaiaClientApplication.audioCurationRepository` → `AudioCurationRepository.fetchACInfo()`(L256/303)；`handleSetANCMode`(L648) → `setMode(Context,I)`(L712–720) |
| ANC V2 | `AncV2Handler.smali` | `GaiaClientService.getQtilManager()`(L529/700/1231/1968) → `AncV2Plugin.fetchCurrentMode()/setCurrentMode(I)`；`QTILFeature.ANC_V2`(L1465)；订阅者 `implements AncV2Subscriber` |
| EQ | `EqHandler.smali` | `musicProcessingRepository`(L153/170 fetchData、L692/712 `setBluetrumBandGain`、L797 `selectSet`、L1184 `setUserSetBandGain`) |
| 电量 | `DeviceInfoHandler.smali` | `handleGetBatteryLevel`(L963) → `batteryRepository`(L1034) → `BatteryRepositoryImpl` → `GetBatteryLevelsRequest`/`GetSupportedBatteriesRequest` → `GaiaClientService.getRequestManager().execute`(L104/112/159/167) |

四大 handler 在 `FlutterNative.smali` 中**无条件 new 并注册**（EqHandler L649-666、AncHandler L670-687、AncV2Handler L691-708、DeviceInfoHandler L750；15 个 handler 数组 L960–1113），`NativeMethodDispatcher.smali` L81–118 逐个转发 `handleMethod(call,result)`，**无任何 chip 判断门控**。

**最硬的一条证据**——连蓝讯/Airoha 的 EQ 都是 GAIA request：

- `SetAirohaPeqRequest.smali` L735 `run()` → L739 `getQtilManager()` → L747 `getMusicProcessingPlugin()` → L759 bytesLE → **L763 `MusicProcessingPlugin.setAirohaPeq([B)`** → L799 `setAirohaPeqStorage`
- `SetBluetrumBandGainRequest.smali` L1928 `run()` → L2059 `getQtilManager()` → L2067 `getMusicProcessingPlugin()` → **L2107 `setBluetrumSetGains(IID[I[D[I[D)V`**

GAIA vendor 常量（`QTILVendorIDs.smali`）：`QTIL_V1V2_VENDOR_ID=0xa`(L7)、`QTIL_V3_VENDOR_ID=0x1d`(L9)，与二手 PROTOCOL.md 的 0x000A/0x001D 完全一致。

### 2.3 chipType 的真实作用：配置 GAIA 自身的 BLE 封装【一级】

`LeGattChipConfig.smali`（高通类）持有 `volatile String chipType`(L9)；`setChipType`(L109-176) 转小写存储；`isAiroha()`(L75-107) 仅比较 `== "airoha"`（`const-string "airoha"` L83）。

调用方 `LeGattClient.smali`：`isAiroha()` 为真时用 `unwrapAirohaPayload`(L1113-1169) / `wrapAirohaPayload`(L1171+) 对 GATT payload 去/加 **4 字节头 `FF 01 00 00`**（`AIROHA_SPECIAL_HEADER`，array at L82-88，长度常量 0x4 L20/60）。

→ **Airoha 机型的日常控制依然是 GAIA over LE GATT，只是外层多 4 字节壳**。这不是"另一套协议"，而是 GAIA 的厂商适配封装。

### 2.4 `isQualcomm` 与协议栈无关【一级 + 推断】

`_re\libapp.strings.txt` 全库仅 1 处 `, isQualcomm: `(L4150)，夹在 WebView/webkit 属性字面量之间（L4140 `, isDirectionalLockEnabled:`、L4161 `, javaScriptBridgeEnabled:`）；ml_smali 全树 grep `isQualcomm` **零命中**。

【推断】某 device-model 对象的 `toString()` 序列化字段，既非协议栈判断也非 native UI 判断。功能差异靠运行时能力探测（`QTILFeature`、`handleGetIfFeatureExists` L2628）与 ANC V2/V3 区分。

---

## 3. 三种私有通道的功能定位

### 3.1 Bluetrum 9ECA（BleSourceSwitch）—— 唯一承担日常控制的私有通道，但不含 ANC【一级】

- 通道：`native/ble/source/` 独立 BLE GATT 连接，service `9ECA0000-7F3A-4F32-9A38-A91B2C6E0100` / write `…0001` / notify `…0003`（`BleSourceSwitchUuids.smali` L36/57/78）。帧 `[0xA5][0x01][cmdId][len][payload]`，无序号无 CRC（详见主文档 §4）。
- `BleSourceSwitchFlutterHandler.smali` `handleMethod`(L17758–18687) 只分发 `bleSource*` 方法：音源切换、EQ（preset/PEQ/preGain/commit）、MIC 增益、音量、SN/颜色/语言/固件查询、断电、连接管理。
- **无 ANC**：`native\ble\source\` 全目录 grep `setAnc|AncMode|NoiseCancel|ancEnabled|ANC_` = **0 命中**；子目录仅 `eq\`/`identity\`/`mic\`。
- 与 GAIA 是否物理并存：ble\source 全目录**无** `GaiaClientApplication|disconnectGaia` 引用，代码层无主动断 GAIA 的互斥逻辑【一级】；是否真能并存受系统 BLE 连接数限制，代码无法断定【推断】。

→ 这是三种私有通道中**唯一做日常控制**的，但它控制的是音源/EQ/MIC，**ANC 仍归 GAIA**。

### 3.2 Airoha 私有栈（SPP/RACE）—— OTA + 设备信息【一级】

- `AirohaHandler.smali` `handleMethod`(L5402) 仅 6 个方法：`airohaGetOtaVersion`/`airohaStartFota`/`airohaApplyNewFirmware`/`airohaPrepareFota`/`airohaSetPeq`/`airohaConnectBT`。
- `handleAirohaConnectBT`(L1155)：先 `connectionRepository.disconnect()`(L1172-1188) 断开 GAIA → `setPreferredProtocol(PROTOCOL_SPP)`(L1355) → `SPP_UUID="00001101-0000-1000-8000-00805F9B34FB"`(L135)。OTA 用 Airoha SDK 私有 SPP，与 GAIA 互斥（先断）。
- `airohaSetPeq`(L2680)：本地 `NativePeq.setParam/setPeqPoint/generateCofe`(L3379/3434/3455) 只算系数，**最终 L4162-4170 取 `musicProcessingRepository`、L4222 `MusicProcessingRepository.setAirohaPeq(ID[I[D[I[D[B)V` 仍走 GAIA 下发**。
- `com\airoha\` SDK 虽含完整控制能力（`AirohaDeviceControl.setAncSetting` L208、`PEQControl.setEQSetting` L33、`NativeAnc158x/1568`），**全 app grep 这些符号 = 0 调用**；`AirohaDeviceControl` 仅被 `FotaManager.smali` L3166 `getDeviceInfo`(L3183) 使用。

→ Airoha 私有栈只做 **OTA + 取设备信息**；EQ/ANC 都走 GAIA。PEQ 只是本地算系数，下发仍走 GAIA。

### 3.3 Jieli 私有栈（SPP RCSP）—— 纯 OTA【一级】

- `JieliFotaHandler.smali` `handleMethod`(L5534) 仅 `startJieliFota`(L5552)/`cancelJieliFota`(L5581)。`handleStartJieliFota`(L2478)：`"jieli_ota_spp"`(L2974/3337)、`"Jieli firmware update connection"`(L2978)、`startOTA`(L4592)。
- OTA 前主动断 GAIA：`Failed to disconnect Gaia service before Jieli OTA`（`_re\libapp.strings.txt` L26459 + L28275-28280 系列）。
- 无私有 EQ/ANC：全 app grep `setJieliPeq|jieliSetPeq` = 0；`EqHandler.smali` 只有 `setBluetrumPEQ`(L1628)/`setPEQ`(L1677)/`getPEQ`(L1706) 三分支，**无 Jieli 分支**。
- 【推断】Jieli 若需 EQ，走 GAIA 通用 `setPEQ`（无 Jieli 专属绑定证据）。

---

## 4. 协议分层总表（一级证据）

| 功能层 | 承载协议 | 与 chipType 关系 |
|---|---|---|
| 连接 / 发现 | 高通 GAIA `ConnectionRepository`（BLE GATT 或 RFCOMM SPP，由 transportType 决定） | 无关 |
| ANC（V1/V2/AudioCuration） | GAIA `AudioCurationRepository` / `AncV2Plugin` | 无关 |
| EQ | GAIA `musicProcessingRepository`（含 Airoha/Bluetrum 专用 set 命令） | 无关 |
| 电量 / 设备信息 / 提示音 / 空间音频 / 灯光 / 触摸 / codec | GAIA qtil 各 Plugin/Repository | 无关；按 `QTILFeature` 能力探测 |
| 音源切换 / 音量 / MIC / SN / PEQ(9ECA 体系) | **Bluetrum 9ECA BLE 私有通道**（独立 GATT 连接） | 与 bluetrum 平台相关，但需 UUID 实测确认 |
| OTA | Airoha 私有 SPP / Jieli 私有 SPP RCSP / Bluetrum / GAIA | **由 chipType 选固件与通道** |

一句话：**控制层与平台解耦，只有 OTA 层和 9ECA 特定功能与平台绑定。**

---

## 5. 各机型真实控制协议表

### 5.1 实测确认的 4 款【二手，来自上游真机】

| 机型 | 控制协议 | ANC 路径 | 传输 | 来源 |
|---|---|---|---|---|
| 羽翼 EDGE | GAIA v3 (vendor 0x001D) | AudioCuration SET `[1,2,4,3]` / GET `[0,1,2,3]`（单字节 mode，非位掩码） | BLE GATT | MiuixMoondrop CHANGELOG L137–139 |
| 布丁 PUDDING | GAIA（v1v2 协商 vendor `0x000A`，业务走 QTIL V3 vendor `0x001D`） | ANC_V2 五档 `00 1D 40 04 <mode>` | RFCOMM SPP `00001101` | CHANGELOG L139–140 + **本链路真机日志**（`_logs2\modules_2026-11-15T15_52_31.720557.log` L13746 起） |
| 梦回2 Golden Ages 2 | GAIA v3 | AudioCuration SET `[1,2,4,3]` / GET `[0,1,2,3]` | BLE GATT | CHANGELOG L141–142（FxxkMoondrop 抓包+真机） |
| 太空漫游2 Space Travel 2 | GAIA v3（9ECA 并存） | AudioCuration 同 GA2 | BLE GATT（`9ECA…` 服务并存） | CHANGELOG L143–144 |

**布丁日志是唯一的本链路一级实证**：`GAIA payload: 000A830000040301`（negotiation，API 3.1）、`001D410300`（ANC_V2 读）、`001D1B000102`（电量查询）、`001D1B01016402640355`（电量响应 左0x64 右0x64 盒0x55）、`001D410401`（ANC 写）——全程 `00001101` RFCOMM，无 9ECA。

### 5.2 其余机型【架构推断，非逐款实测】

依据 §2 的一级架构证据：**既然 app 内所有 `connectDeviceBT` 都进 GAIA、所有 ANC/EQ handler 都走 GAIA，那么只要是 app 支持的蓝牙 TWS 耳机，其日常控制协议就是 GAIA**（BLE GATT 或 RFCOMM SPP，由该机型的 transport 决定），与云端 `chipType` 无关。

- HyperDrop `DeviceModels.kt` 的 45 款中，除上述 4 款外，其余 41 款（含官方标 `bluetrum` 的 32 款、标 `jieli` 的 2 款 pudding/mirage 之外的机型等）**没有逐款实机证据**。按架构应为 GAIA，但具体是 `BLE_GAIA` 还是 `RFCOMM_GAIA`、ANC 是 `ANC_V2` 还是 `AudioCuration`、增益映射如何，**必须实机探测**。
- 标 `bluetrum` 的机型可能额外暴露 9ECA 服务（音源/EQ/MIC），但**不能假设所有 bluetrum 机型都用 9ECA 做日常控制**，也不能假设 9ECA 有 ANC。
- 标 `qualcomm` 的 4 款（Alice/littlewhite/Sparks/Voyager）按架构走 GAIA，符合预期。
- 标 `airoha` 的 The Garden：日常控制走 GAIA over LE GATT + `FF 01 00 00` 封装；私有 SPP 仅 OTA。

---

## 6. 对 HyperDrop 的实际意义

1. **现有 43 款 inferred 默认 `RFCOMM_GAIA` 的方向是对的**（控制层确实统一 GAIA）。真正要补的是**逐款的 transport（BLE vs SPP）与 ANC 命令路径（ANC_V2 vs AudioCuration）**，这需要实机探测。
2. **设备识别应基于 UUID/能力探测，不是设备名 alias**：app 靠 `resolveChipTypeForConnection(uuid)` 判平台、靠 `QTILFeature` 能力探测判功能。HyperDrop 的 `DeviceModels.match` 按名字 `contains` 是根本差异。
3. **实现 GAIA over BLE**：`connectDeviceBT` 的 `LOW_ENERGY` 分支是主流（多数机型 BLE），HyperDrop 目前只接通 RFCOMM，`PodTransport.BLE_GAIA` 枚举未落地——这是最大缺口。
4. **9ECA 定位要摆正**：它是 bluetrum 机型的**附加**日常控制通道（音源/EQ/MIC），不是 ANC 替代品；实现它可补齐这些机型的功能，但不要指望它控制 ANC。先用 UUID 扫描确认目标机型是否暴露 `9ECA…` 服务。
5. **Airoha 封装**：若支持 The Garden，需处理 GATT payload 外层 `FF 01 00 00` 4 字节头（`wrapAirohaPayload`/`unwrapAirohaPayload`），私有 SPP 只留给 OTA。
6. **OTA 通道仲裁**：沿用 `OWNER_QUALCOMM_RFCOMM`/`OWNER_JIELI_OTA_SPP` 与"OTA 前断 GAIA"逻辑，扩展 YieldPolicy。

---

## 7. 诚实边界

- **单一编译产物**：本文只证明**这一份 APK** 内 GAIA 是跨芯片统一控制层，不能外推到所有官方 app 版本或所有耳机型号。
- **无逐款实机**：除 Pudding（本链路日志）与二手上游的 4 款外，其余机型未逐款抓包验证；"架构推断"不等于"实测确认"。
- **9ECA 物理并存**：代码层看不到互斥逻辑，但实际 BLE 连接数限制下能否与 GAIA 真并存，需实机验证。
- **"音乐/游戏模式"非 GAIA**：二手记录显示布丁四击切换模式时 btsnoop 窗口内 GAIA 零帧——这是**固件内部状态**，无 app 侧通道，属功能层面例外，不是机型控制协议例外。

---

## 附录：本轮证据索引

- `pudding\_work\ml_smali\com\moondroplab\moondrop\moondrop_app\native\handlers\BluetoothConnectionHandler.smali`：连接分派（L1683/1724-2311/1969/2036/5200/5220/5706）
- `...\handlers\AncHandler.smali`：AudioCuration ANC（L232/279/326/648）
- `...\handlers\AncV2Handler.smali`：ANC_V2（L529/1231/1968/2303）
- `...\handlers\EqHandler.smali`：EQ（L153/692/797/1184）
- `...\handlers\DeviceInfoHandler.smali`：电量（L963/1034）
- `...\handlers\AirohaHandler.smali`：Airoha 私有（L1155/2680/4162/4222/5402）
- `...\handlers\JieliFotaHandler.smali`：Jieli OTA（L2478/5534）
- `...\handlers\BluetrumFotaHandler.smali`：Bluetrum OTA（L4241）
- `...\handlers\BleSourceSwitchFlutterHandler.smali`：9ECA 功能面（L17758-18687）
- `...\native\ble\source\*.smali`：9ECA 帧/命令/UUID（详见主文档 §4）
- `pudding\_work\ml_smali\com\qualcomm\qti\gaiaclient\...`：GAIA SDK（1131 文件）
  - `core\gaia\data\qtil\data\qtildata\LeGattChipConfig.smali`（L9/75-107/109-176）
  - `core\bluetooth\client\le\LeGattClient.smali`（L82-88/1113-1169/1171+，AIROHA_SPECIAL_HEADER）
  - `core\requests\qtil\SetAirohaPeqRequest.smali`（L735-799）、`SetBluetrumBandGainRequest.smali`（L1928-2107）
  - `core\gaia\data\qtil\data\QTILVendorIDs.smali`（L7/L9）
- `_logs2\modules_2026-11-15T15_52_31.720557.log` L13746 起：Pudding GAIA over RFCOMM 实机 trace
- 二手：`pudding\research\refs\MiuixMoondrop\MiuixMoondrop\{CHANGELOG.md,ADAPTATION.md,PROTOCOL.md}`
