# 官方 Moondrop Link app 逆向：不同耳机的通信协议分析

> 目标：为 HyperDrop 适配更多协议/设备提供依据。
> 分析对象：`moondrop link.apk`（111,071,316 B，SHA256 `04756B49ACFEA523758D86101C96C1824B7B209AE3A8836C07837088E795D2D5`），
> 包名 `com.moondroplab.moondrop.moondrop_app`，Flutter + 原生混合架构。
> 工作副本：`pudding\research\apps\moondrop link.apk`，解包目录 `_re\apk`。

## 0. 证据等级与方法

| 等级 | 来源 | 说明 |
|---|---|---|
| **直接证据（一级）** | `pudding\_work\ml_smali\com\moondroplab\moondrop\moondrop_app\native\ble\source\*.smali` | 完整反编译 smali，含全部数值常量与帧逻辑。**9ECA 协议的最终判据** |
| 直接证据 | `_re\libapp.strings.txt`（394,879 行，Dart AOT） | 路由表、方法名、日志串、芯片型号 |
| 直接证据 | `pudding\research\scripts\moondroplink.strings.txt`（106,960 行，dex） | 类名、UUID、SDK 符号 |
| 直接证据 | `_re\apk\lib\arm64-v8a\*.so.strings.txt` | 各芯片 native 库算法符号 |
| **二手（仅对照）** | `pudding\research\refs\MiuixMoondrop\MiuixMoondrop\{PROTOCOL.md,ADAPTATION.md}` | GAIA 逐字节命令表、机型档案；**与官方 smali 冲突处以 smali 为准** |

重要教训：`strings.txt` 是排过序的字符串字面量，数值常量被编译进字节码，**搜不到命令 ID/魔数**。协议字节细节必须看 smali。

**下载 URL 不可用**：`Invoke-WebRequest` 报「基础连接已经关闭: 接收时发生错误」，`curl.exe` 报 `schannel: AcquireCredentialsHandle failed: SEC_E_NO_CREDENTIALS`。改用工作区已有官方 APK 副本。

## 1. 核心机制：连接统一走 GAIA，chipType 只做平台/OTA 标记（已按 smali 一级证据修正）

> ⚠️ **本节早期表述"按 UUID 解析 chipType 再分发到对应平台 handler"已被 smali 一级证据推翻。**
> 完整结论见 [CONTROL_PROTOCOL_BY_MODEL.md](CONTROL_PROTOCOL_BY_MODEL.md)。要点：

**连接层**：`connectDeviceBT` **不按 chipType 分支**，所有连接都进高通 GAIA 的 `ConnectionRepository.connect()`（`BluetoothConnectionHandler.smali` L2036）。唯一分支是 `transportType`：`LOW_ENERGY`→BLE GATT、`CLASSIC`→RFCOMM SPP（L5706/5852/5860），**与厂商无关**。日志串 `connectDeviceBT: setChipType failed, continue connecting.`（L7410）证明 chipType 不是连接必要条件。

**chipType 的真实作用**：Dart 层 `resolveChipTypeForConnection(uuid)` 解析出字符串 → `handleSetChipType`(L5200) 写入高通类 `LeGattChipConfig.setChipType(String)`(L5220)。它只影响 GAIA 内部 BLE 包封装：Airoha 机型由 `LeGattClient.wrapAirohaPayload` 加 4 字节头 `FF 01 00 00`（`AIROHA_SPECIAL_HEADER`）——**控制仍是 GAIA**。

**控制层**：ANC（`AncHandler`→`AudioCurationRepository`、`AncV2Handler`→`AncV2Plugin`）、EQ（连 `SetAirohaPeqRequest`/`SetBluetrumBandGainRequest` 都走 `getMusicProcessingPlugin()`）、电量（`BatteryRepository`）**全部收敛到 GAIA**，四大 handler 无条件注册、无 chip 门控。

**私有协议栈只做 OTA 与特定功能**：Airoha/Jieli 私有 SPP 仅 OTA（OTA 前先断 GAIA）；Bluetrum 9ECA 是唯一承担日常控制的私有通道，但只有音源/EQ/MIC/音量，**无 ANC**。

相关字符串证据：`=====>connectDeviceBT: resolved chipType='`（L7409）、`resolveChipTypeForConnection error: uuid=`（L7456）、`resolveUsbChipType error: uuid=`、Flutter 方法 `_resolveChipTypeForConnection@249487043`、`ChipType`（L24670）。

**机型→功能列表是服务端下发**（`getDeviceFunc`/`loadSupportedBluetoothDevices(productType=)`），本地按 UUID 匹配。
→ 对 HyperDrop 的启示：**识别应基于 UUID / VID:PID + 能力探测，而非设备名字符串**（当前 `DeviceModels.kt` 正是按名字 alias 匹配，这是根本差异）。

## 2. 平台总览（app 实际支持的芯片栈）

| 平台 | 日常控制协议（smali 一级证据） | 私有通道用途 | 识别特征 | OTA 固件 |
|---|---|---|---|---|
| Qualcomm GAIA (QTIL) | **GAIA**（BLE GATT `00001100` / SPP `00001101`） | — | GAIA UUID 族、vendor 0x001D/0x000A | 通用 |
| Bluetrum 中科蓝讯 | **GAIA** + 附加 **9ECA**（音源/EQ/MIC，无 ANC） | 9ECA 日常控制 + OTA | `9ECA0000-…-A91B2C6E0100` | `.fot`（按 SKU） |
| Airoha 络达 | **GAIA over LE GATT**（payload 加 `FF 01 00 00` 头） | 私有 SPP/RACE 仅 **OTA + 设备信息** | `CHAR-/PRIM-…AirohaBLE`、`AIROHA_SPECIAL_HEADER` | `airoha-ota.bin` |
| Jieli 杰理 | **GAIA** | 私有 SPP RCSP **仅 OTA**（先断 GAIA） | RCSP、`jl_bt_ota` | `jieli-ota.ufw` |
| Synopsys 新思 | USB HID | — | `com.syna.cxaudiohidinterface` | `synopsys-ota.hid` |
| Comture dB Effects | USB HID | — | Conexant CX Audio HID、report id 0xF3 | — |
| Conexant CX2070x | USB HID（ANC/EQ 参数类） | — | `com.conexant.cnxtusbcheadset` | — |
| Actions 炬芯 | — | 无 | 无 | ❌ 无实现 |
| Bes 恒玄 | — | 仅 `/bes` 路由占位 | 无 | ❌ 空壳 |

**关键结论**：蓝牙耳机的日常控制（含 ANC/EQ）**一律 GAIA**，与 chipType 无关；私有栈只服务 OTA（Airoha/Jieli）或特定功能（Bluetrum 9ECA）。详见 [CONTROL_PROTOCOL_BY_MODEL.md](CONTROL_PROTOCOL_BY_MODEL.md)。

路由表（`_re\libapp.strings.txt` L4890–5024）：`/airoha` `/bes` `/bluetrum` `/bluetrum10` `/bluetrumUsbSettingV2` `/bluetrumusb` `/comture` `/jieli` `/jieliusb` `/qualcomm` `/synopsys`，以及功能页 `/anc` `/ancV2` `/adaptiveancV2` `/liveANC` `/peqSettingBTV3` `/touchV1..V4` `/lhdc` `/ldac` `/lc3` `/spatialaudio` `/gain` `/led` `/sensor` `/dybass` `/spvAncUSB` `/factorySppTest` 等（功能按芯片分版本）。

## 3. Qualcomm GAIA（高通 QTIL）—— HyperDrop 唯一已实现的协议

- **SDK**：`com.qualcomm.qti.gaiaclient.core.*`（`GaiaManagerImpl`/`GaiaReader`/`GaiaSenderWrapper`/`GaiaCommand`、`ANCInfo`/`CodecInfo`/`EarbudInfo`/`TouchV4Info`/`SpatialAudioInfo` 等）。
- **UUID**（dex L38828–38841）：
  - BLE GATT service `00001100-d102-11e1-9b23-00025b00a5a5`；命令 `…1101`（写）、响应 `…1102`、数据 `…1103`
  - SPP RFCOMM `00001101-0000-1000-8000-00805f9b34fb`；legacy GAIA SPP `00001107-D102-11E1-9B23-00025B00A5A5`
  - CCCD `00002902-0000-1000-8000-00805f9b34fb`；A2DP `0000110b`、HFP `0000111e`
  - 常量 `QUALCOMM_UUID`、`QTIL_V1V2_VENDOR_ID`、`QTIL_V3_VENDOR_ID`（dex L66365–66369）
- **线格式**（二手 PROTO L29–52，app 侧由 `libutils-lib.so` 的 `TransportProtocolNative_calculateChecksumNative` 与 `BytesUtilsNative` 佐证大端）：
  ```
  [vendor u16 BE][commandValue u16 BE][payload]
  commandValue = (feature << 9) | (type << 7) | (cmd & 0x7F)
  vendor: 0x001D=V3, 0x000A=V1/V2（版本探测 00 0A 03 00）
  type: 0=COMMAND, 1=NOTIFICATION, 2=RESPONSE, 3=ERROR
  ```
- **Feature ID**（二手 PROTO L142–174）：0 BASIC / 1 EARBUD / 2 ANC_V1 / 8 AUDIO_CURATION / 13 BATTERY / 14 VOICE / 15 DAC_GAIN / 16 CODEC / 18 SPATIAL / 19 LED / 20 ONEBRINGTWO / 24 POWER / 27 DYBASS / 29 AUDIO_FILE_STORAGE / 30 LR_CHANNEL / 32 ANC_V2。
- **关键命令**（二手，与 HyperDrop `GenericGaiaCommandTable.kt` 完全一致）：能力 `00 1D 00 01`；电量 `00 1D 1A 00` / `01 01 02`；ANC_V2 读 `00 1D 40 03` 写 `00 1D 40 04 <mode>`；AudioCuration 读 `00 1D 10 03` 写 `00 1D 10 04 <bitmask 1/2/4>`；LHDC `00 1D 20 05/06`；提示音 `00 1D 1C 01/02`；增益 `00 1D 1E 01/02`；LED `00 1D 26 01/02`；双连 `00 1D 28 01/02`；空间 `00 1D 24 01/02`。
- **产测 SPP**：`/factorySppTest`、`FactorySppNative`、`OWNER_QUALCOMM_RFCOMM`（dex L64643）。
- **通道所有权**：`OWNER_QUALCOMM_RFCOMM` / `OWNER_JIELI_OTA_SPP`——多平台复用同一 RFCOMM 通道时需要抢/让所有权（对应 HyperDrop 的 YieldPolicy 心跳租约）。

## 4. 中科蓝讯 9ECA 私有协议（BLE 音源切换 / EQ / SN / MIC）—— 一级证据，最重要

这是本轮逆向最有价值的成果：官方 app 的反编译 smali 给出了**完整、无歧义**的帧定义，且**推翻了二手参考文档的部分描述**。

### 4.1 GATT 特征（`BleSourceSwitchUuids.smali`）

| 角色 | UUID |
|---|---|
| SERVICE | `9ECA0000-7F3A-4F32-9A38-A91B2C6E0100` |
| COMMAND（写） | `9ECA0001-7F3A-4F32-9A38-A91B2C6E0100` |
| NOTIFICATION（通知） | `9ECA0003-7F3A-4F32-9A38-A91B2C6E0100` |
| CCCD | `00002902-0000-1000-8000-00805f9b34fb` |

`9ECA0002` 在代码中无引用（跳号，疑为保留特征）。该服务与 GAIA 在**同一 GATT 连接内并存**。

### 4.2 帧结构（`BleSourceSwitchFrames.smali`）

```
HEADER_SIZE = 0x4,  MAGIC = 0xA5,  PROTOCOL_VERSION = 0x01,  MAX_PAYLOAD_SIZE = 0xFF

偏移  长度  含义
 0     1    MAGIC = 0xA5
 1     1    VERSION = 0x01
 2     1    command / notification ID (u8)
 3     1    payload 长度 (u8, 0..255)
 4..   N    payload（原样，不加密）
```

**关键修正**：**没有序列号字段，没有 CRC/校验和字段**。二手 PROTOCOL.md L476 写的 `[A5][01][frameType][cmdId][seq][len][payload]`（6 字节头 + seq）与官方 smali **不符**；以 smali 为准。多字节整数**全部小端**（`BleProtocolBytesKt`：`encodeI16LittleEndian`/`encodeU16LittleEndian`/`u8`）。

切换音源只需一帧：`SET_AUDIO_SOURCE(0x02)` + `USB_AUDIO(0x01)` → `A5 01 02 01 01`。

### 4.3 命令 ID（`BleSourceCommand.smali`，`<init>` 第三个参数为线上 id）

| 命令 | ID | 命令 | ID |
|---|---:|---|---:|
| GET_FW_VERSION | 0x00 | GET_PRESET_EQ | 0x07 |
| GET_AUDIO_SOURCE | 0x01 | SET_PRESET_EQ | 0x08 |
| SET_AUDIO_SOURCE | 0x02 | GET_PEQ_CONFIG | 0x09 |
| GET_CAPABILITY | 0x03 | SET_PEQ_PREGAIN | 0x0A |
| SET_POWER_OFF | 0x04 | GET_PEQ_POINT | 0x0B |
| GET_DEVICE_VOLUME | 0x05 | SET_PEQ_POINT | 0x0C |
| SET_DEVICE_VOLUME | 0x06 | COMMIT_PEQ | 0x0D |
| GET_MIC_GAIN | 0x0E | SET_MIC_GAIN | 0x0F |
| GET_EARBUD_COLOR | 0x11 | GET_EARBUD_LANGUAGE | 0x13 |
| GET_EARBUD_SN_LEFT | 0x15 | GET_EARBUD_SN_RIGHT | 0x17 |

（身份类 0x11/0x13/0x15/0x17 由 `$values()` ordinal 16/17/18/19 与 `<init>` 参数一致性证明。）

### 4.4 状态码 / 通知 / 音源 ID

- 状态码（`BleSourceStatusCode.smali`，payload 第 0 字节）：OK 0x00 / FALSE 0x01 / DENIED_BY_MODE 0x02 / SAME_SOURCE 0x03 / REVISION_CONFLICT 0x04 / READ_ONLY_PRESET 0x0E / INVALID_OFFSET 0x10 / STORAGE_ERROR 0x11 / INFO_UNAVAILABLE 0x12 / UNKNOWN 0xFF。
- 通知 ID（`BleSourceNotificationId.smali`，走 NOTIFICATION 特征 `9ECA0003`）：PRESET_EQ_CHANGED 0x86 / PEQ_COMMITTED 0x87 / MIC_GAIN_CHANGED 0x88。
- 音源 ID（`BleSourceSwitchFrames$SourceId.smali`）：蓝牙 0x00 / USB Audio 0x01 / 2.4G 0x02 / AUX 0x03 / 光纤 0x04 / 同轴 0x05 / HDMI ARC 0x06 / 本地播放 0x07 / 自动 0x7F / 无信号 0xFE / 未知 0xFF。

### 4.5 三个子协议

- **EQ**（`eq\`）：SET_PRESET_EQ payload 1B index 0..7；SET_PEQ_PREGAIN payload `[0x07, lo, hi]` I16LE，范围 -12800..12799（0.01 dB）；SET_PEQ_POINT payload 8B `[index 0..31][freq U16LE 20..20000][gain I16LE][Q U16LE 0.001][filter u8 0..7]`；COMMIT_PEQ payload `[action 0..2][revision U16LE]`。`BlePeqFilter`：PEAKING 0 / LOW_SHELF 1 / HIGH_SHELF 2 / LOW_PASS 3 / HIGH_PASS 4 / BAND_PASS 5 / NOTCH 6 / ALL_PASS 7 / UNKNOWN 0xFF。
- **身份/SN**（`identity\`）：COLOR 0x11 / LANGUAGE 0x13 空 payload，响应 2B `[status][value]`；SN_LEFT 0x15 / SN_RIGHT 0x17 payload = offset（0 或 10），响应 14B `[status][total=20][offset][chunkLen=10][10B SN 片段]`——两段 10B 拼成 20 字符 SN。
- **MIC 增益**（`mic\`）：GET 0x0E 空；SET 0x0F payload 2B I16LE，范围 -1280..1280（0.1 dB）；响应 9B `[status][current][min][max][step]`（全 I16LE）；通知 8B 无 status。

**数值均为物理值×系数的 int16LE，不是 IEEE 浮点也不是 Q 位域**（preGain ×100、micGain ×10、PEQ gain 原始 int16）。

## 5. Airoha 络达（ABI15xx/15xx）

> **控制协议修正（smali 一级证据）**：Airoha 机型日常控制（ANC/EQ）**仍走 GAIA over LE GATT**，仅在 GATT payload 外层加 4 字节头 `FF 01 00 00`（`AIROHA_SPECIAL_HEADER`，`LeGattClient.wrapAirohaPayload`）；私有 SPP/RACE 栈**只用于 OTA + 取设备信息**（OTA 前先断 GAIA）。`com\airoha\` SDK 虽含 `setAncSetting`/`PEQControl`，全 app 零调用。详见 [CONTROL_PROTOCOL_BY_MODEL.md](CONTROL_PROTOCOL_BY_MODEL.md) §3.2。以下为平台能力清单，非控制协议结论。

- **传输**：BLE GATT + SPP + LE Audio — 类族 `AirohaGATT`/`AirohaSPP`/`AirohaLEA`（dex L40218–40277）。
- **私有 UUID**（ASCII 编码，dex L39116/39117/39123）：`CHAR-\x2DAB-1AirohaBLE`、`CHAR-\x2DAB-2AirohaBLE`、`PRIM-\x2DAB-03AirohaBLE`；同组 ISSC UUID `49535343-…`（L39118–39120）。
- **方法**：`airohaConnectBT`/`airohaGetOtaVersion`/`airohaPrepareFota`/`airohaStartFota`/`airohaApplyNewFirmware`/`airohaSetPeq`，固件 `airoha-ota.bin`（FLUT L49701–49708）；SDK 多代 FOTA 管理器 `AirohaFotaMgr1568`/`AirohaFotaMgrEx1562`/`AirohaLeaFotaMgr`/`AirohaRaceFotaMgr`/`AirohaMMI1568`/`AirohaMMI158x`。
- **帧识别特征**：常量 `AIROHA_SPECIAL_HEADER` / `AIROHA_SPECIAL_HEADER_LENGTH`（dex L39739/39740）——RACE 协议头。
- **native 算法**：`libnative-anc.so`（`Adapt_IIR_API_Get_ANC_ptr`、`Airoha_Native_ANC`、JNI `getAncCoef`）；`libnative-anc-1568.so`（AB1568 PZ/SZ 前馈/反馈 FIR 自适应，`output_ff_gain`/`pz_fir_st`/`sz_invert_st`/`uu_thd`，日志路径 `/sdcard/Documents/Airoha/Log/AB1568/…`）；`libnative-anc-158x.so`（并行 FF 响应 + Eigen FFT）；`libnative-peq.so`（`AB1530PEQ_NATIVE`、官方 SVN `Rev#21384`、`calcZ`/`generateCofe`/`setPeqPoint`）。
- **芯片型号串**：AB155x / AB1562 / AB1562E / AB1565 / AB1568 / AB157x / AB158x / AB159x 及 `_DUAL`/`_DUAL_CHIP`/`_V3` 变体。
- 具体机型：云端 `products_all` 显示仅 **MOONDROP The Garden** 一款为 `airoha`，其余 109 款无 Airoha（见 §9.2）。

## 6. Jieli 杰理（RCSP over SPP）

- **OTA**：`Jieli OTA: starting SPP OTA with address:`（FLUT L28280）——**明确走 SPP RFCOMM**；固件 `jieli-ota.ufw`；方法 `startJieliFota`/`cancelJieliFota`/`initJieliOTA`；路由 `/jieli`、`/jieliusb`。
- **状态机**（dex L48657–48668）：`mandatory upgrade mode` / `normal mode` / `reconnecting: addr=` / `doubleBackup=` 能力协商 / `OWNER_JIELI_OTA_SPP`。
- **切换关键点**：OTA 前必须先断开 GAIA — `Failed to disconnect Gaia service before Jieli OTA`、`disconnectGaiaService`、中文日志「等待 Gaia 断开超时 / Gaia 已断开，可以建立独立产测 SPP 连接」（FLUT L230589）。说明**不同芯片协议复用同一物理通道，必须释放 GAIA 所有权**。
- **native 认证**（`libjl_ota_auth.so`，直接证据）：RCSP 协议 `com/jieli/jl_bt_ota/impl/RcspAuth`；`CRC16`、`cd03_crc_encode`；随机挑战加密鉴权 `getEncryptedAuthData`/`getRandomAuthData`/`decrypt`；`JLUFW` 固件魔数；`parse_fw_info`；设备身份 `pid`/`uid`/`jpid`/`juid`。

## 7. Synopsys 新思 / Comture dB Effects（USB HID）

- **Synopsys**：固件 `synopsys-ota.hid`（.hid OTA）；页面 `package:moondrop_app/pages/device_usb/tab_page/synopsys/{OTAPage,PEQPage,ToneTuningPage}.dart`；`libCxAudioHidLib.so` 属 Synopsys（`com.syna.cxaudiohidinterface`，Conexant CX Audio HID）——USB HID `GetVendorID`/`GetProductID`/`ClaimHidInterface`/`ReadInputReport`/`WriteOutputReport`、magic byte `0x4C`、S37 patch、`Upgrade *.hid file`。
- **Comture**：`comtureCheckIsConnected`/`comtureConnectUsbDevice`/`comtureGetFilter`/`comtureSetFilter`/`comtureGetGainLevel`/`comtureSetLedState`/`comtureSetVolume` 等；原生 7 字节 HID report（report id `0xF3`，`HID_CMD_REPORT_ID_F3`）；`DacCommandHandler` 收发。
- **EQ 格式转换（HyperDrop 可直接借鉴）**：`convertQualcommToSynopsysFilter`、`convertSynopsysToQualcommFilter`、`convertQualcommToSpvFilter`、`convertSpvToQualcommFilter`（FLUT L52903–52907）——高通与 Synopsys/SPV 的 PEQ 滤波器参数可互转。
- 另有 **SPV** 平台（`/spvAncUSB`、`/spvPromptToneUSB`、`/spvTouchUSB`、`DacProfile`、`_buildSpvDac*`），与 Conexant/USB DAC 体系相关。

## 8. Actions 炬芯 / Bes 恒玄：无实质实现

- **Actions**：全量搜索命中均为 Flutter framework 的 `Actions` 类、GAIA SDK 的 `EarbudActions`/`ACActions`，**与炬芯无关**；无炬芯 UUID / OTA 方法 / native 库。
- **Bes**：仅路由 `/bes` 占位（与 `/airoha`/`/jieli` 并列），无任何 `isBes`/`BesOta`/`BesHandler`、无私有 UUID、无固件、无 native 库。
- 若要适配这两家，无法从官方 app 获得协议依据，需另找 SDK 或抓包。

## 9. 机型→芯片（平台）映射：云端 API 给出权威答案，但 ≠ 控制协议

最初从 APK 字符串无法把芯片和机型绑定（只有通用 SKU 名）。官方云端 `GET /api/v1/products/all` 的**每一条产品记录都带显式 `chipType` 字段**，共 110 款，补上了「机型→硅平台/OTA 体系」的缺口。

**关键辨析（用户纠正，已被 app 自身字符串证实）**：`chipType` 表示硅平台与其 OTA 固件/通道，**不等同于日常控制协议**。证据：`Failed to disconnect Gaia service before Jieli OTA`、`Jieli OTA: failed to disconnect Gaia service`（`_re\libapp.strings.txt` L26459 / L28278-28279）——日常控制走 GAIA，Jieli 私有 SPP 只在 OTA 时接管；`Jieli OTA: starting SPP OTA with address:`（L28280）；`OWNER_QUALCOMM_RFCOMM` vs `OWNER_JIELI_OTA_SPP`（dex L64642-64643）。最直接的反证就是 **Pudding/Mirage 官方标 `jieli`，但 HyperDrop 用 GAIA wire（`PuddingCommandTable`）实机验证可用**。因此下游消费 chipType 时只能用于「平台/OTA 资源选择」，**不能直接推导控制协议**；控制协议须实机探测/抓包确认。

### 9.1 拉取方式与产物

- 脚本：`_re\fetch_api.py`（urllib + 严格 SSL；系统 curl/Invoke-WebRequest 因 `SEC_E_NO_CREDENTIALS` 不可用）。
- 原始数据：`_re\api\products_all.json`（110 款）、`ota_configs_all.json`（199 条固件）、`products_ota_version.json`（110 款版本）。
- **可直接消费的映射**：`_re\api\chip_mapping.json` / `chip_mapping.csv`（110 款 × name/uuid/chipType/route/platformAndOta/controlProtocol=待实测/firmwareExt/eqBands/isTws），生成脚本 `_re\build_chip_map.py`。
- 单产品详情 `_re\api\product_detail_sparxie.json` 含完整 `appResourceRoot` 对象（`name:"Jieli"`、`uri:"/jieli"`）——证明 `chipType` 与功能页面路由一一对应。

### 9.2 官方 chipType 分布（110 款）—— 是平台/OTA，不是控制协议

| chipType | 款数 | 平台 + OTA/通道（据本轮逆向） | 日常控制协议 | appResourceRoot |
|---|---:|---|---|---|
| bluetrum 中科蓝讯 | 43 | `.fot` OTA；另有 BLE 9ECA 私有通道(音源/EQ/SN/MIC) | **待实测**（9ECA 是否日常控制未知） | `/bluetrum` |
| synopsys 新思 | 28 | USB HID（Conexant CX，`.hid`）或 WIRED | USB HID / 预设 EQ | `/synopsys` |
| spv | 14 | USB DAC（`.bin`），Conexant/SPV 体系 | USB | `/spv` |
| jieli 杰理 | 5 | SPP RCSP OTA（`.ufw`，需先断 GAIA） | **可能是 GAIA**（Pudding/Mirage 已证实） | `/jieli` |
| jiu | 5 | USB DSP（`.bin`） | USB | `/jiu` |
| comture | 5 | dB Effects USB HID（report id 0xF3） | USB HID | `/comture` |
| qualcomm 高通 | 4 | OTA 走 GAIA | GAIA（已证实） | `/qualcomm` |
| jieliusb | 3 | 杰理 USB | USB HID/音频 | `/jieliusb` |
| bluetrumusb | 2 | 蓝讯 USB（UsbSetting V2） | USB | `/bluetrumusb` |
| airoha 络达 | 1 | BLE 私有 CHAR/PRIM-AirohaBLE + SPP/RACE；OTA `airoha-ota.bin` | BLE/SPP 私有 | `/airoha` |

`chipType` 与 `appResourceRoot.uri` 完全一一对应（见 `_re\analyze_chips.py` 交叉表），即云端下发的是「机型所属平台及其功能页面」，**不是**「日常控制用哪套协议」。

### 9.3 对 HyperDrop 的影响（结论已收敛）

把 HyperDrop `DeviceModels.kt` 的 45 款（2 verified + 43 inferred，全部 inferred 默认 `RFCOMM_GAIA`）与官方 `chipType` 对照（`_re\compare_hyperdrop.py`）：

| 官方 chipType | HyperDrop 款数 | 说明 |
|---|---:|---|
| bluetrum | 32 | 平台为蓝讯；**是否有 GAIA 控制通道须逐款实测**，不可直接判定"非 GAIA" |
| jieli | 4 | 含 **pudding、mirage**——实机验证 GAIA 可用，证明杰理芯片也跑 GAIA 控制协议 |
| qualcomm | 4 | Alice / littlewhite / Sparks / Voyager，高通平台 |
| bluetrumusb | 2 | MM3A / Nicole Reeyn，USB 平台 |
| airoha | 1 | The Garden |
| 未匹配 | 2 | MOONDROP MACA/MOCA、PILLS（别名与云端名不一致） |

**修正后的结论**：不能由 chipType 判定"协议栈选错了"。Pudding/Mirage（jieli 平台 + GAIA 控制）就是现成反例——芯片厂商与控制协议是正交的两件事。chipType 的正确用途是**选 OTA 固件/资源、判断可能存在的私有通道**（如蓝讯 9ECA、杰理 OTA SPP），而控制协议仍需按实际 Bluetooth UUID/特征探测或抓包确认。之前"43 款中选错协议"的表述是过度推论，已撤回。

**可执行建议**：把这份映射作为**平台提示**加入 `DeviceProfile`（例如 `platform: jieli/bluetrum/...`），用于 OTA 资源选择与"优先探测哪些私有通道"；不要用它覆盖现有已验证的 `transports`/`commandTable`。对未验证的 43 款，正确做法是补一次 UUID/特征扫描 + 抓包实测，而不是按 chipType 批量改协议。

### 9.4 固件扩展名 ↔ 芯片交叉验证

`ota_configs_all` 199 条固件的扩展名分布（`_re\analyze_api.py`）：`.fot` 91 / `.bin` 63 / `.txt` 21 / `.ufw` 11 / `.hid` 6 / `.png` 1 / 无扩展名 6。文件名含芯片型号 `BT8931H`×3 / `BT8932F`×1 / `BT8952F`×2 / `AB031`×1。扩展名与 chipType 对应：`.fot`→bluetrum、`.ufw`→jieli、`.hid`→synopsys、`.bin`→spv/jieliusb/jiu/comture，与 §5–§7 的逆向结论一致。

### 9.5 其他云端资源（非协议，供参考）

- `protocolv2/getNewest` 名字像协议定义，**实际是用户协议/隐私政策文本**（version 1.1.0），不是蓝牙协议。
- 需鉴权/参数而返回 400 的端点：`ota-config-equalizers/all`、`ota-config-voices/all`、`ota-config-app-resources/all`、`prompt-tone-otas/all`、`peq-configs/all`。
- 成功拉取的非协议资源：`refresponse_all.json`、`freq_response_all.json`（1128 条频响）、`wallpapers_all.json`（44 张）、`icons_all.json`、`policies_all.json`。

### 9.6 二手参考中已实测的机型结论（标注来源）

`ADAPTATION.md`/`CHANGELOG.md`（二手，**非本轮逆向直接产物**）：EDGE = GAIA v3 over BLE（AudioCuration，SET 位掩码 1/2/4、GET 0-based）；PUDDING = GAIA v4 over RFCOMM（ANC_V2 五档，三路电量）；梦回2/Golden Ages 2 = AudioCuration SET `[1,2,4,3]` GET `[0,1,2,3]`；太空漫游2 = 蓝讯 BT8932F + 9ECA。**注意**：官方 API 把 EDGE 标为 `bluetrum`（与二手"GAIA v3"冲突），以官方 chipType 为准，二手实测值仅作总线行为参考。

## 10. 与 HyperDrop 现状对比及适配建议

| 维度 | 官方 app | HyperDrop 现状 |
|---|---|---|
| 设备识别 | UUID / VID:PID → chipType → 能力探测 | 设备名 alias 匹配（`DeviceModels.kt`），45 款中 43 款为「协议推断」 |
| 传输 | BLE GAIA + SPP GAIA + 9ECA BLE + Airoha BLE/SPP + Jieli SPP + USB HID | 仅 RFCOMM/GAIA（`PodTransport` 虽有 `BLE_GAIA`/`BLE_SRC_9ECA` 枚举但未接通） |
| 9ECA | 完整实现（本轮已得全部字节） | 枚举存在、协议未实现 |
| 多平台 | 8 家芯片 | 仅 GAIA 一家 |
| EQ 格式 | 高通↔Synopsys/SPV 互转 | 仅高通 GAIA |

**建议优先级**（已按 smali 一级证据修订，详见 [CONTROL_PROTOCOL_BY_MODEL.md](CONTROL_PROTOCOL_BY_MODEL.md) §6）：
0. **控制层方向确认：统一 GAIA，不要按 chipType 改协议栈**：smali 证明 `connectDeviceBT` 无条件进 GAIA、所有 ANC/EQ 走 GAIA。现有 43 款 inferred 默认 GAIA 的方向正确；真正要补的是逐款 **transport（BLE vs SPP）** 与 **ANC 命令路径（ANC_V2 vs AudioCuration）**，这必须实机探测。
1. **实测补全的是 transport/命令路径，不是协议族**：对 43 款 inferred 机型补 BLE GATT/SPP 的 UUID + 特征扫描与抓包，确定走 BLE 还是 SPP、ANC 是 ANC_V2 还是 AudioCuration；参考 §2 各平台的识别特征。
1b. **设备识别改造**：接入 BLE 扫描，按 service UUID 分派 —— `00001100…`→GAIA over BLE、`9ECA…`→蓝讯 9ECA 附加通道（音源/EQ，非 ANC）、Airoha `PRIM-/CHAR-…AirohaBLE`→络达（GAIA + 4 字节封装）、仅 SPP→GAIA over RFCOMM；USB 按 VID:PID。蓝牙耳机的 ANC 无需按平台分派（一律 GAIA）。
2. **实现 9ECA 作为 bluetrum 的附加功能通道**（投入产出比高）：帧仅 4 字节头、无校验、命令表完整（§4）；音源切换一帧 `A5 01 02 01 <src>`；EQ/SN/MIC 子协议均已明确。可直接对接已有的 `PodTransport.BLE_SRC_9ECA` 枚举位——但先用 UUID 扫描确认目标机型是否真的暴露 9ECA 服务（§4.1），不要假设所有 32 款 bluetrum 都用它；**它只管音源/EQ/MIC，不含 ANC（ANC 仍走 GAIA）**。
3. **打通 GAIA over BLE（最大缺口）**：`connectDeviceBT` 的 `LOW_ENERGY` 分支是主流（多数机型 BLE），HyperDrop 目前只接通 RFCOMM、`PodTransport.BLE_GAIA` 未落地。service `00001100` + 特征 `1101/1102/1103` + CCCD `2902`；RFCOMM 现有 `GaiaTransport` 可复用命令表。
4. **通道仲裁**：参考 `OWNER_QUALCOMM_RFCOMM`/`OWNER_JIELI_OTA_SPP` 与 Jieli OTA 前断 GAIA 的逻辑，扩展现有 YieldPolicy 心跳租约，支持跨协议让出通道。
5. **EQ 适配层**：移植 `convertQualcommToSynopsysFilter` 等转换函数。
6. **OTA 分发**：按 chipType + SKU 选择 `.bin`/`.ufw`/`.hid` 资源；蓝讯需维护 MAC→SKU 缓存。
7. Actions/Bes：官方 app 无依据，暂不投入，除非另有 SDK。

## 附录 A：本轮产出的分析资产

### 云端 API（追加轮）
- `_re\fetch_api.py` / `_re\probe_api.py`：拉取脚本（urllib，绕过 schannel 证书问题）。
- `_re\api\products_all.json`（110 款含 chipType）、`ota_configs_all.json`（199 固件）、`products_ota_version.json`、`product_detail_sparxie.json`、`refresponse_all.json`、`freq_response_all.json`、`wallpapers_all.json`、`icons_all.json`、`policies_all.json`、`protocolv2_getNewest.json`。
- **`_re\api\chip_mapping.json` / `chip_mapping.csv`：110 款机型→平台/OTA→待实测控制协议→固件的映射（`controlProtocol` 字段标记"需实测"，勿当控制协议结论消费）。**
- `_re\analyze_api.py` / `_re\analyze_chips.py` / `_re\compare_hyperdrop.py` / `_re\build_chip_map.py` / `_re\inspect_detail.py`：分析脚本。

### APK 逆向
- `_re\apk\`：官方 APK 解包目录（1472 文件）。
- `_re\extract_strings.py`：字符串提取脚本（utf8/ascii/utf16le 三种 run）。
- `_re\libapp.strings.txt`、`_re\apk\lib\arm64-v8a\*.so.strings.txt`：Dart/native 字符串。
- ble-source 子代理结论依据：`pudding\_work\ml_smali\com\moondroplab\moondrop\moondrop_app\native\ble\source\*.smali`（BleSourceSwitchFrames/Uuids/Command/StatusCode/NotificationId、eq\、identity\、mic\ 子包、BleProtocolBytesKt）。
- **控制协议专题（本轮追加，一级证据）**：[CONTROL_PROTOCOL_BY_MODEL.md](CONTROL_PROTOCOL_BY_MODEL.md)。依据 dispatch/gaia-core/private-channels 三代理对 `pudding\_work\ml_smali` 的逐类反编译，确认"控制统一 GAIA、chipType 不选协议、私有栈只做 OTA/特定功能"，并推翻本文早期 §1 的分派表述。
- 关键 smali：`...\native\handlers\{BluetoothConnectionHandler,AncHandler,AncV2Handler,EqHandler,DeviceInfoHandler,AirohaHandler,JieliFotaHandler,BluetrumFotaHandler,BleSourceSwitchFlutterHandler}.smali`；`com\qualcomm\qti\gaiaclient\...`（1131 文件，含 `LeGattChipConfig`/`LeGattClient`/`SetAirohaPeqRequest`/`SetBluetrumBandGainRequest`/`QTILVendorIDs`）。
- 实机 trace：`_logs2\modules_2026-11-15T15_52_31.720557.log` L13746 起（Pudding GAIA over RFCOMM，唯一本链路真机日志）。
