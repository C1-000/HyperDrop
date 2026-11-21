# 官方 App 逆向分析 —— 全量复核与潜在错误审计

> 用途：对本次会话从开始到现在的**全部结论**做一次回源复核，把"确证错误 / 表述不严 / 无法证实 / 已派生为代码缺陷"四类分开，避免错误继续被下游文档和实现照抄。
> 复核方式：所有判定都回到**一级证据**（官方 APK 反编译产物、官方 CDN 抓包、本机实机日志），不采信二手文档（`MiuixMoondrop/PROTOCOL.md` 等）作为事实来源。
> 编写时间：2026-11-21。适用于 APK `SHA256=04756B49ACFEA523758D86101C96C1824B7B209AE3A8836C07837088E795D2D5`（包名 `com.moondroplab.moondrop.moondrop_app`）。

---

## 0. 证据等级约定

| 等级 | 含义 | 本次使用的来源 |
|---|---|---|
| **L1** | 官方二进制/字节码直接可读的常量与结构 | `pudding/_work/ml_smali/**`（dex 反编译）、`_re/apk/lib/**`（native）、`_re/libapp.strings.txt`（Dart AOT 字符串） |
| **L2** | 官方服务端真实返回 | `_re/api/*.json`（`cdn-service.moondroplab.tech`） |
| **L3** | 本机实机运行日志 | `_logs2/verbose_2026-11-14T20_55_01.254776.log`、`_logs2/modules_2026-11-15T15_52_31.720557.log` |
| **L4** | 第三方二手记录 | `pudding/research/refs/MiuixMoondrop/**`、上游 `FxxkMoondrop` 等 —— **只能当线索，不能当结论** |
| **L5** | 纯推断 | 无直接证据，仅由命名/上下文推出 |

---

## 1. 结论先行

会话期间共有 **9 条确证错误**（其中 3 条已派生进 HyperDrop 代码），**9 条表述不严**，**4 条至今无法证实**。其中影响最大的三条：

1. **`GET_SUPPORTED_FEATURES` 不是位图**，是 `(featureId, version)` 二元组数组。HyperDrop 按位图解析（`GenericGaiaCommandTable.kt:179-191`），**当前必然解析出垃圾 feature 集合**。
2. **`AudioCuration SET_MODE` 不是 0x01/0x02/0x04 位掩码**，是单字节 mode 号。此前"EDGE 用位掩码"的说法来自二手文档，smali 不支持。
3. **GAIA 电池响应里没有充电位**。`value & 0x80` 是旧 OPPO AA 协议的约定，被误带进 GAIA 路径；同时 HyperDrop 的电量解析用 `level in 0..100` 过滤，一旦固件真的置了高位，**该路电量会被整条丢弃**。

另有一条"结构性"发现：`PodTransport` 声明了 `BLE_GAIA` / `BLE_SRC_9ECA`，但**全仓库没有任何代码读取这个字段**，连接路径只有 SPP 一条（`DeviceSession.kt:587-588`）。这意味着即使把 43 款推断机型改成 BLE，当前也跑不起来 —— 这是本次复核里对"适配更多设备"影响最大的一条（详见 D4）。

另一条方法论级的修正：**`chipType` 与"日常控制协议"正交**（前期判断错误，已由 `m00504` 用户纠正并在文档中撤回）。`chipType` 只决定 OTA 固件资源与是否存在私有通道，不能用来选控制协议栈。

---

## 2. 会话关键结论逐条判定

| # | 结论（会话中曾出现） | 判定 | 依据 |
|---|---|---|---|
| 1 | 官方 app 支持 8 个芯片平台（Qualcomm/Airoha/Bluetrum/Jieli/Synopsys/Comture/SPV/JIU） | 成立，但"平台"含义需限定 | L2：`products_all.json` 110 款 chipType 分布；L1：路由 `/airoha` `/bluetrum` `/jieli` `/synopsys` `/comture` `/spv` `/jiu` |
| 2 | 日常控制统一走 GAIA，私有栈只做 OTA/特定功能 | 成立（native 层 L1 证实） | L1：`QTILV3Vendor.smali:2335` 无条件注册 25 个 V3 插件；`LeGattChipConfig` 只在 BLE 载荷前加 4 字节 |
| 3 | `chipType` 决定控制协议 | **错误，已撤回** | 反证：Pudding/MIRAGE 官方标 `jieli`，但实机走 GAIA over RFCOMM（L3 日志） |
| 4 | 43 款推断机型"选错协议栈" | **错误，已撤回** | 同 #3；且 `setChipType failed, continue connecting`（`_re/libapp.strings.txt:7410`，`7408` 是 `resolve chip type failed: `、`7409` 是 `resolved chipType='`）说明 chipType 非连接必要条件 |
| 5 | `GET_SUPPORTED_FEATURES` = 位图 | **错误** | L1：`GetSupportedFeaturesData.smali:81-131` 每 2 字节 `(featureId, version)` |
| 6 | 9ECA 帧 = 6 字节头（含 seq + CRC） | **错误**（来自二手文档） | L1：`BleSourceSwitchFrames.smali:17/21/23/25` → `[A5][01][cmdId][len][payload]` |
| 7 | `AudioCuration SET_MODE` = 位掩码 01/02/04 | **错误**（来自二手文档） | L1：`V3AudioCurationPlugin.smali:7894-7925` → `sendPacket(0x04, mode)` 单字节 |
| 8 | GAIA 电量含充电位 `0x80` | **错误**（OPPO AA 遗留） | L1：`BatteryLevel.smali:17/19` 只有 `LEVEL_OFFSET=1`、`LEVEL_UNKNOWN=0xff`，无 mask |
| 9 | `00 1D 41 03 00` 是 app→耳机命令 | **错误** | L3：该帧后紧跟 `noise mode: ui=1 wire=0`，是响应；`0x41` 高字节即 type=RESPONSE |
| 10 | `00 0A 83 00 00 04 03 01` 中 `0x83` 是 commandValue、末位 `0x01` 是能力位 | **部分错误** | L1：`V1V2Packet.smali:195-300` → `0x8000` 是 ack 位；`V2ApiVersion.smali:25-127` → payload `[protocolVersion][major][minor]` |
| 11 | `protocolv2/getNewest` 是蓝牙协议资源 | **错误** | L2：实际返回用户协议/隐私政策文本（version 1.1.0） |
| 12 | 官方"无机型分支、只有一套全局定义" | **部分成立，需限定** | L1 可证 native 层无分支；Dart AOT（`libapp.so`）层**无法证实/证伪** |
| 13 | `EDGE` 官方 chipType=bluetrum 与二手"GAIA v3"冲突 | **不构成冲突** | chipType=平台，控制协议可独立；措辞需改 |
| 14 | `Actions`（炬芯）/`Bes`（恒玄）无实现 | **证据不足的证否** | 仅字符串检索阴性；`/bes` 路由存在，`/actions` 未证实存在 |
| 15 | 9ECA 命令集无 ANC | 成立，但属 grep 阴性 | L1：`native/ble/source/**` 全目录 `setAnc|AncMode|NoiseCancel` 零命中 |
| 16 | ANC_V2 feature value = `0x20`（非 ordinal `0x1f`） | 成立 | L1：`QTILFeature.smali:993-1016`；L3 实机 `00 1D 40 03` 佐证 |
| 17 | 官方从不启用 ANC V1 插件 | **部分错误** | L1：`QTILV3Vendor.smali:268-1131` 的 25 个**预注册**插件中确实没有 `V3ANCPlugin`；但同文件 `buildPlugin(Feature)` 的 `:pswitch_167` 分支（L1140-1152）会 `new-instance V3ANCPlugin` → 设备在 `GET_SUPPORTED_FEATURES` 里上报 featureId=2 时仍会被动态构建（详见 A7） |
| 18 | Airoha BLE 载荷前加 `FF 01 00 00` | 成立 | L1：`LeGattClient.smali:82-88` array-data `-0x1,0x1,0x0,0x0` |

---

## 3. A 类：确证的事实错误（必须修正，会被照抄）

### A1 `GET_SUPPORTED_FEATURES` 是二元组数组，不是位图

- **错误说法**：payload 是 32 位 bitmask words，`bit n` 置位表示支持 feature n。
- **事实（L1）**：`pudding/_work/ml_smali/com/qualcomm/qti/gaiaclient/core/gaia/qtil/data/GetSupportedFeaturesData.smali`
  - 常量 L7-15：`FEATURES_LIST_OFFSET=0x1`、`FEATURE_ID_PAIR_OFFSET=0x0`、`FEATURE_VERSION_PAIR_OFFSET=0x1`、`MORE_DATA_OFFSET=0x0`、`MORE_DATA_VALUE=0x1`
  - ctor L33-153：L58 `getUINT8(payload,0)` = `hasMoreData`（`==1` 表示还有下一页，需再发 `V1_GET_SUPPORTED_FEATURES_NEXT=0x02`）
  - 循环 L81-131：`v2` 从 1 起，每轮 `getUINT8(payload, v2)` 取 featureId、`getUINT8(payload, v2+1)` 取 version，`new Feature(id, version)`，`add-int/lit8 v2, v2, 0x2`
- **偏移要小心**：官方 `GetSupportedFeaturesData` 的输入是 `ResponsePacket.getData()`，而 `getData()` 返回的正是 `V3Packet.payload` —— 也就是 `V3PacketFactory.buildPacket` 里 `getSubArray(buf, 4)` 之后的段（`V3Packet.smali:9` `PAYLOAD_OFFSET=0x4`）。所以官方视角的 `byte[0]` = **HyperDrop 视角的 `payload[4]`**：

  | 官网偏移 | HyperDrop `payload` 偏移 | 含义 |
  |---|---|---|
  | data[0] | payload[4] | `hasMoreData`（`0x01` = 还有下一页） |
  | data[1] | payload[5] | 第 1 个 featureId |
  | data[2] | payload[6] | 第 1 个 feature 的 version |
  | data[3] | payload[7] | 第 2 个 featureId |
  | … | … | 每 2 字节一组 |

- **影响**：HyperDrop 现有实现会把 `(0x00,0x01)`、`(0x08,0x01)` … 解释成互不相干的 bit，得到完全错误的 feature 集合；如果将来用它驱动 UI 显隐，**功能会随机消失或出现**。
- **正确实现**：
  ```kotlin
  // payload = [vendorHi][vendorLo][cmdValHi][cmdValLo][hasMore][id0][ver0][id1][ver1]...
  val hasMore = payload[4].toInt() and 0xFF == 0x01
  val features = mutableSetOf<Int>()
  var i = 5                                    // 注意：不是 4
  while (i + 1 < payload.size) {
      features += payload[i].toInt() and 0xFF  // featureId
      // payload[i + 1] 是 version，按需保留
      i += 2
  }
  ```
  分页：`hasMore == true` 时再发 `00 1D 00 02`（`V1_GET_SUPPORTED_FEATURES_NEXT=0x02`），把结果累加。

### A2 `AudioCuration SET_MODE` 是单字节 mode，不是位掩码

- **错误说法**：`00 1D 10 04 01/02/04`，`0x01=ANC`、`0x02=通透`、`0x04=自适应` 的位掩码。
- **事实（L1）**：`.../plugins/v3/V3AudioCurationPlugin.smali`
  - `setInfo(ACInfo, Object)` 方法体内 L6941 `const/4 v6, 0x4`（把命令号 0x04 放进 v6，供后续所有分支复用）
  - `Mode` 分支 L7894-7925：L7908 `instance-of Mode` → L7912 `Mode.getValue()` → L7922 `sendPacket(v6 /*=0x04*/, mode)` → `V3Plugin.sendPacket(II)`（`V3Plugin.smali:785-840`）内部 `new byte[1]` + `aput-byte` 写入 payload
  - `V3AudioCurationPlugin$COMMANDS.smali`：`V1_GET_CURRENT_MODE=0x3`、`V1_SET_MODE=0x4`
- **影响**：发送端的单字节写法（HyperDrop `setNoiseMode` 的 AUDIO_CURATION 分支）**本身是对的**；错的是"这是位掩码"的解释。真正的风险在于：如果按位掩码语义去组合（比如想同时表达 ANC+自适应），会发出非法 mode 号。
- **附带事实**：`Mode` 的**响应**是多字节（`Mode.smali:52-259`，`constructor <init>(I[B)`）：data[0]→`value`、data[1]→`featureTypeValue`、data[2]→`adaptationControlSupport`、data[3]→`gainControlSupport`、版本 ≥5 时 data[4]→`howlingDetectionControlSupport`（`if-ge p1, 0x5` → 低于 5 直接 `Support$NotSupported`，见 L67-86）。HyperDrop 的响应解析只取 `payload[4]` 一个字节，**丢掉了 adaptation/gain/howling 支持位** —— 这正好是官方用来判断"该挡位是否支持增益调节"的信息，是漏用而非错误。

### A3 9ECA 帧是 4 字节头，无序列号、无 CRC

- **错误说法**（二手 `PROTOCOL.md`）：`[A5][01][frameType][cmdId][seq][len][payload]`，6 字节头 + CRC。
- **事实（L1）**：`.../native/ble/source/BleSourceSwitchFrames.smali`
  - 常量 L17 `HEADER_SIZE=0x4`、L21 `MAGIC=0xa5`、L23 `MAX_PAYLOAD_SIZE=0xff`、L25 `PROTOCOL_VERSION=0x1`
  - 组帧 L845-934：`len+4` → `[0]=0xA5`（`const/16 v2,-0x5b`）、`[1]=0x1`、`[2]=cmdId`、`[3]=payloadLen`，payload 从 offset 4
- **结论**：`[A5][01][cmdId][len][payload]`，**没有 seq，没有 CRC**，多字节整数全小端。

### A4 `001D410300` 是响应帧，不是命令帧

- **事实（L3）**：`_logs2/verbose_2026-11-14T20_55_01.254776.log` L22135-22136：
  ```
  GAIA payload: 001D410300
  noise mode: ui=1 wire=0 previous=1
  ```
  紧跟其后的解析日志说明这是入站帧。同样地 L22197 `GAIA payload: 001D410401` → L22198 `noise mode: ui=2 wire=1 previous=2`，而命令方是 L22196 的 `set noise mode: ui=2 wire=1`（app 侧发起，紧随其后就是响应）。
- **机制**：`commandValue = (feature<<9) | (type<<7) | cmd`（`V3Command.smali:161-225`），`V3PacketType.smali`：COMMAND=0 / NOTIFICATION=1 / RESPONSE=2 / ERROR=3。因此**高字节 = `(feature<<1) | (type>>1)`**，**低字节 = `((type & 1) << 7) | cmd`**：

  | feature | COMMAND(0) | NOTIFICATION(1) | RESPONSE(2) | ERROR(3) |
  |---|---|---|---|---|
  | 2 ANC | 高`04` 低`cmd` | 高`04` 低`80\|cmd` | 高`05` 低`cmd` | 高`05` 低`80\|cmd` |
  | 8 AUDIO_CURATION | 高`10` 低`cmd` | 高`10` 低`80\|cmd` | 高`11` 低`cmd` | 高`11` 低`80\|cmd` |
  | 0x20 ANC_V2 | 高`40` 低`cmd` | 高`40` 低`80\|cmd` | 高`41` 低`cmd` | 高`41` 低`80\|cmd` |

  所以"`0x40xx`=命令、`0x41xx`=响应"只是 `feature=0x20` 这一特例；换成 `feature=8` 就是 `0x10xx`=命令、`0x11xx`=响应，`feature=2` 是 `0x04xx`/`0x05xx`。写成通用规则应为"高字节 = `(feature<<1) | (type>>1)`"，否则跨 feature 会误判方向。

### A5 GAIA v1v2 协商帧被误读

- **帧（L3）**：`_logs2/verbose_2026-11-14T20_55_01.254776.log:22133` → `GAIA payload: 000A830000040301`（紧随 `:22134` 的 `GAIA negotiation response: 000A830000040301`）
- **事实（L1）**：`core/gaia/core/v1v2/packets/V1V2Packet.smali`
  - L7 `ACKNOWLEDGMENT_MASK=0x8000`、L11 `COMMAND_MASK=0x7fff`
  - ctor L195-300：`vendor=getUINT16(buf,0)`；`w=getUINT16(buf,2)`；`isAcknowledgement = (w & 0x8000) != 0`；`command = w & 0x7fff`；`payload = buf[4..]`
- **解算**：vendor=`0x000A`；`w=0x8300` → ack=true，command=`0x0300`（= `GET_API_VERSION`，见 `V2ApiVersionFetcher.smali:82` 与 `V1V2QTILPlugin$COMMANDS.smali:20`）；payload=`00 04 03 01`
- **payload（L1）**：`core/version/V2ApiVersion.smali:25-127` → `[0]=protocolVersion`、`[1]=apiVersionMajor`（同时赋给 `gaiaVersion`）、`[2]=apiVersionMinor`。→ **protocolVersion=0，GAIA 应用版本 4.3**
- **此前错误**：把 `0x83` 当作 v3 的 commandValue、把末位 `0x01` 当作能力位。末位字节 `V2ApiVersion` 根本不读取。
- **名词陷阱（见 B7）**：这个 4.3 是 **GAIA API 版本**，不是"QTIL V3"的版本；同一台机同时用 vendor `0x000A`（v1v2）协商版本、vendor `0x001D`（QTIL V3）跑业务。

### A6 GAIA 电池响应没有充电位

- **事实（L1）**：`core/gaia/qtil/data/battery/BatteryLevel.smali` 只有 `LEVEL_OFFSET=0x1`、`LEVEL_UNKNOWN=0xff`；每条记录 2 字节 `[batteryId][level]`。
- **错误来源**：旧 OPPO AA 协议（`pudding/src/puddingpods/app/src/main/java/moe/chenxy/oppopods/pods/Packets.kt:315/359-360/414-415`）用 `value & 0x7F` 取电量、`value & 0x80` 取充电。该约定**不适用于 GAIA**。
- **实机佐证（L3）**：`001D1B01016402640355` → 左 `0x64`=100、右 `0x64`=100、盒 `0x55`=85；三个值都 `<0x80`，无法据此判断高位是否存在。
- **当前风险**：`HyperDrop/app/src/main/java/cythen/hyperdrop/device/protocol/GenericGaiaCommandTable.kt:120-136` 用 `if (level in 0..100)` 过滤。若某固件真的置了充电位（`0x80|level`），该值落在 128..228，**会被整条丢弃**，UI 上表现为"该路电量消失"。建议改为 `val lv = level and 0x7F; val charging = (level and 0x80) != 0` 并**同时**保留不做充电判断的容错。

### A7 ANC_V1 路径：请求语义错，且响应解析永不命中

这是本次复核新发现的、**此前从未提及**的确定性缺陷。

- **官方事实（L1）**：
  - `V3ANCPlugin$COMMANDS.smali`：`V1_GET_ANC_STATE=0x1`、`V1_SET_ANC_STATE=0x2`、`V1_GET_CURRENT_ANC_MODE=0x4`、`V1_SET_ANC_MODE=0x5` …
  - `QTILV3Vendor.smali` **预注册表**（constructor，L268-1131，共 25 个）**没有** `V3ANCPlugin`；但同文件 `private buildPlugin(Feature)`（L390-1273）的 `:pswitch_167` 分支（L1140-1152）里有 `new-instance ...V3ANCPlugin`，紧邻的 `:pswitch_171` 是 `V3EarbudPlugin`。也就是说：**官方不在连接初始化时预注册 ANC V1 插件，但保留了"设备上报 feature 2 就动态构建"的路径**。
- **HyperDrop 现状**：
  - `GenericGaiaCommandTable.kt:27` `queryNoiseMode` 的 ANC_V1 分支发 `00 1D 04 01` = `feature=2, cmd=0x01` = **GET_ANC_STATE**（问"ANC 开没开"），不是 GET_CURRENT_ANC_MODE（`0x04`）
  - `GenericGaiaCommandTable.kt:53` `setNoiseMode` 发 `00 1D 04 02 <wire>` = **SET_ANC_STATE**，其 payload 语义是 0/1 开关，却塞进了 8 档映射后的 mode 值
  - `GenericGaiaCommandTable.kt:107-112`：`ancResponseCmd` 对 ANC_V1 取 `0x05`，并要求 `sub == 0x03 || sub == 0x04`。而 GET_ANC_STATE 的响应是 `00 1D 05 01`（高字节 `05`、低字节 `01`）→ **`sub=0x01` 永远不等于 03/04，该分支永不命中**
- **结论**：`AncPathKind.ANC_V1` 整条路径在语义和解析上都站不住。要么删除，要么改成 `GET_CURRENT_ANC_MODE=0x04` / `SET_ANC_MODE=0x05` 并把 sub 判定改为 `0x04/0x05`。
- **额外提醒**：即便改对，官方对常规耳机不会预注册该插件，只有在耳机上报 feature 2 时才会动态构建，所以基本没有"官方已验证行为"可参照，只能靠实机试。

### A8 `chipType` ≠ 控制协议（已撤回，但早期产物仍在）

- **曾下结论**：chipType 决定协议栈，43 款推断机型选错了栈。
- **已撤回（m00504 用户纠正 + L1/L3 佐证）**：`setChipType failed, continue connecting. error=`（`_re/libapp.strings.txt:7410`；L7408=`resolve chip type failed: `、L7409=`resolved chipType='`）；`resolveChipTypeForConnection` 失败不阻断连接；Pudding/MIRAGE 官方 `chipType=jieli` 而实机走 GAIA。
- **残留物**：`_re/api/chip_mapping.json|csv` 曾把 chipType 写成 `transport` 字段，现已改为 `platformAndOta` + `controlProtocol="需实测"`。**如果还有别处引用了旧版 CSV，需要一并核对。**

### A9 `protocolv2/getNewest` 不是蓝牙协议

- 名字极具误导性，实际返回用户协议/隐私政策文本（`version 1.1.0`）。
- 所有"从云端拿到了协议定义"的表述都应该删除。云端**没有**任何 ANC 挡位/功能列表/命令映射（`products_all.json` 字段全集已核对：只有 `eqBands/gainRange/qRange/isTotalGainSupported/isPreGainSupported/supportedFilterTypes/isGamingHeadphone/isDacAmp/isDongleUpgradable/dongleUpgradeTypes/retrieveSkuViaSn/chipType` 等，无功能清单）。

---

## 4. B 类：表述不严，需要加限定条件

### B1 "官方无机型分支"只对 native 层成立

- 可证（L1）：`AncV2Handler.smali:1886-1968` 读 `mode` → `check-cast Integer` → 仅校验 `0<=mode<6` → 直传 `setCurrentMode(I)`，无查表；`AncHandler.smali:648-720` 同样透传；`L23-33` 六个 `MODE_*` 常量是死常量（全文只在声明行出现）。
- 不可证：Dart 层编译进 `libapp.so`（AOT snapshot），只能提取字符串，**不能反编译出控制流**。因此正确表述是"**native 层已排除机型查表；Dart 层无法证实或证伪**"，而不是"官方没有机型分支"。

### B2 `EDGE` 的 chipType 与控制协议不冲突

官方 API 把 `MOONDROP EDGE` 与 `MOONDROP EDGE 2` 都标为 `bluetrum`；二手记录称其走 GAIA v3 over BLE。二者**可以同时为真**（chipType=平台）。原文档写"冲突，以官方 chipType 为准"应改为"不构成冲突；EDGE 的实际控制路径仍需实测"。

### B3 `Actions`/`Bes` 的"无实现"是证否不足

- `Bes`：存在路由 `/bes`（`_re/libapp.strings.txt`），但无 handler、无 UUID、无协议常量。
- `Actions`：全库 `Actions` 命中均为 Flutter framework / GAIA SDK 的 `Actions` 类，与炬芯无关；`/actions` **未证实存在**。
- 正确表述："在本 APK 的字符串与类清单中未发现炬芯/恒玄的可执行协议实现"，而不是"官方不支持"。

### B4 9ECA"无 ANC"是 grep 阴性

`native/ble/source/**` 全目录（含 `eq/`、`identity/`、`mic/` 子包）对 `setAnc|AncMode|NoiseCancel|ancEnabled|ANC_` 零命中，`BleSourceCommand` 命令集也确为音源/音量/EQ/MIC/SN/断电。**这个结论强度足够**，但要注明是"命令集枚举完整 + 关键词零命中"的双重阴性，而非"官方明说不支持"。

### B5 `V3BatteryPlugin` 的 `V1_GET_SUPPORTED_BATTERIES` 值

`V3BatteryPlugin$COMMANDS.smali` 只显式声明 `V1_GET_BATTERY_LEVELS:I = 0x1`。`SUPPORTED_BATTERIES=0x0` 是**推断**（未声明即取 0），不是读到常量。文档里应标注推断。

### B6 `isQualcomm` 是 L5 推断

`_re/libapp.strings.txt:4150` 的 `", isQualcomm: "` 夹在 WebView 属性字面量之间（L4140 `isDirectionalLockingEnabled`、L4161 `javaScriptBridgeEnabled`），`ml_smali` 全库 grep `isQualcomm` 零命中。目前判断"与协议无关的 toString 字段"属推断，不应作为论据引用。

### B7 "GAIA v4" 与 "QTIL V3" 是两个正交概念

| 名词 | 含义 | 本机实测值 |
|---|---|---|
| GAIA API 版本 | v1v2 协商出来的应用层版本（`V2ApiVersion`） | **4.3**（major=4, minor=3） |
| QTIL vendor | v3 业务走哪个 vendor id | **0x001D**（`QTILVendorIDs.smali:9`）；v1v2 走 `0x000A`（L7） |
| 插件 version | 每个 feature 自己的能力版本（`GetSupportedFeaturesData` 的 version 字节） | 逐 feature 不同 |

因此"Pudding 是 GAIA v4"这句话只在"API 版本"意义上成立，**不能**据此认为它的帧格式是"第四代格式"——它用的就是 `[vendor u16][commandValue u16][payload]` 这套 v3 QTIL 线格式。

### B8 ANC_V2 feature 的 ordinal(0x1f) 与 value(0x20) 必须区分

`QTILFeature.smali:993-1016`：`const/16 v1, 0x1f`（ordinal）+ `const/16 v2, 0x20`（value）+ `"ANC_V2"`。`getValue()`（L1343-1351）返回 `value` 字段。写成 `feature=0x1f` 会直接发错命令。会话中一个子代理曾把 ordinal 当 value 报告（"ANC_V2=0x1f / TOUCHV4=0x1e"），**该报告此点错误**。

### B9 `QTILFeature` 在 0x1b 之后 ordinal 与 value 整体错位

| 枚举 | ordinal | value |
|---|---|---|
| DYBASS | 0x1b | 0x1b |
| AUDIO_FILE_STORAGE | 0x1c | **0x1d** |
| LR_CHANNEL | 0x1d | **0x1e** |
| TOUCHV4 | 0x1e | **0x1f** |
| ANC_V2 | 0x1f | **0x20** |

即从 `AUDIO_FILE_STORAGE` 起 value = ordinal + 1。原因是中间存在未导出/废弃枚举位。**任何"按 ordinal 推算 value"的做法都会在此处出错**，必须逐条读 `const/16 v2, ...`。

---

## 5. C 类：至今无法证实（必须靠实测）

| # | 未决问题 | 为什么 APK 里没有答案 |
|---|---|---|
| C1 | 每一款机型实际走哪条控制路径（ANC_V2 / AudioCuration / 其它） | 官方不靠机型表分派（B1），路径由**固件**决定；app 用 `GET_SUPPORTED_FEATURES` 运行时探测 |
| C2 | Dart 层是否还有一层 mode 语义转换 | `libapp.so` 是 AOT snapshot，只有字符串可读 |
| C3 | 9ECA 与 GAIA 能否在同一物理连接上并存 | 代码层 `ble/source` 无 `disconnectGaia` 引用（L1），但能否并存受 BLE 连接数与固件策略限制（L5） |
| C4 | `EDGE` 实际控制栈 | 官方 chipType=bluetrum，二手记录=GAIA v3 BLE，二者不冲突但都未在本机验证 |

**这四项只能靠真机验证**：对目标耳机开 HCI snoop（`btsnoop_hci.log`），用官方 app 操作一次 ANC/增益，再对齐帧。本仓库目前**没有任何 HCI 抓包文件**，现有实机证据仅覆盖 MOONDROP Pudding（`3B:75:4A:DB:15:40`）一台。

---

## 6. D 类：已派生进 HyperDrop 的缺陷（按风险排序）

### D1 【高】`SupportedFeatures` 位图解析 —— 必然产出错误结果

- 位置：`HyperDrop/app/src/main/java/cythen/hyperdrop/device/protocol/GenericGaiaCommandTable.kt:179-191`
- 现状：`while (i + 3 < payload.size)` 取 4 字节 word，`for (bit in 0..31)` 展开；注释也写成 `00 1D 01 01 <bitmask words>`（L179）；`DeviceSession.kt:1225` 的 KDoc 同样写着 "supported-feature bitmask" —— 这批表述要一起改。
- 修法：见 A1 的二元组实现；同时把 `GaiaReport.SupportedFeatures(val features: Set<Int>)`（`GaiaReport.kt:36`）保留（只装 featureId 即可），version 需要的话新增字段
- 关联：`DeviceSession.kt:1036` 目前只 `Log.i`，修好解析后应真正驱动"该显示哪些功能"

### D2 【中，潜伏】ANC_V1 分支永不命中且语义错误

- 位置：`GenericGaiaCommandTable.kt:27`、`:53`、`:107-112`
- **当前无任何机型会走到**：`DeviceModels.kt` 里 `inferredCaps()` 默认 `path = AncPathKind.AUDIO_CURATION`（L107），pudding 用 `ANC_V2`（L185），`DeviceCapabilities.NONE` 也是 `ANC_V2`（`DeviceCapabilities.kt:55`）——全仓库**没有一处**把 `ancPath` 设成 `ANC_V1`。所以这是**潜伏死路径**，不是现网故障。
- 但仍应处理：详见 A7。建议优先删除该分支（官方只在设备上报 feature 2 时才构建 ANC V1 插件），或改 `0x04`/`0x05` + sub 判定 `0x04/0x05`。

### D3 【中】电量解析丢弃带高位样本

- 位置：`GenericGaiaCommandTable.kt:120-136`、`PuddingCommandTable.kt:118-138`
- 现状：`if (level in 0..100)`；无充电位处理
- 修法：`val lv = level and 0x7F; val chg = (level and 0x80) != 0; if (lv in 0..100) ...`（充电位仅作可选信息，UI 上不要强依赖）

### D4 【中】43 款推断机型一律 `RFCOMM_GAIA`，且 BLE 通道完全没有实现

- 位置：`DeviceModels.kt:147-163`（`inferred()` 固定 `transports = listOf(PodTransport.RFCOMM_GAIA)`，实际出现于 L160/L174/L208/L278 四处）；`DeviceProfile.kt:44` 接口默认值同为 `RFCOMM_GAIA`
- **更严重的事实**：`PodTransport` 声明了三个值（`DeviceModels.kt:13`），但全仓库对 `PodTransport.*` 的引用**只有上面那 5 处赋值**、没有任何读取点或分派逻辑；连接路径只有 SPP 一条 —— `DeviceSession.kt:587-588` `device.createRfcommSocketToServiceRecord(UUID.fromString(commandTable().sppUuid))`，`DeviceSession.kt:824` 重连复用同一函数。**没有 `BluetoothGatt` / `BluetoothLeScanner` 的任何引用**。也就是说 `BLE_GAIA` 与 `BLE_SRC_9ECA` 目前是**纯声明**。
- 事实：官方 110 款产品中**蓝牙 54 款**，其中 `bluetrum 43 / jieli 4 / qualcomm 4 / bluetrumusb 2 / airoha 1`；而 HyperDrop 只有 45 款、仅 2 款 verified
- 风险：大量现代机型（尤其 EDGE 系列）很可能走 **BLE GATT**；即使不走 BLE，`transports` 这个字段也没被任何代码消费，等于"配了不用"
- 建议：先让 `transports` 真正参与连接选择（按扫描到的 UUID：`00001101…` → SPP，`…/1100` → BLE GATT），再补 BLE 传输实现；否则新增机型时仍会硬走 SPP

### D5 【低】`Packets.kt` 的充电位约定与 GAIA 路径混用

- 位置：`HyperDrop/app/src/main/java/cythen/hyperdrop/pods/Packets.kt:315`（注释）、`:359-360`、`:414-415`
- 这些属于旧 OPPO AA 解析；应明确标注"AA 专用"，并确保 GAIA 路径不引用

### D6 【低】`MODE_*` 常量与官方 ordinal/value 命名混用

- 位置：`AncV2Handler.smali:23-33` 对应语义（OFF=0/ON=1/TRANSPARENT=2/ANTI_WIND=3/ADAPTIVE=4/LIVE=5）与 HyperDrop 的 UI status（1..5）是**两套编号**，`PuddingCommandTable.kt:49-60` 已用 `(wireValue, uiStatus)` 显式分离 —— 这个做法是对的，其它 `CommandTable` 实现应对齐同一模式

---

## 7. 复核方法与可复现命令

| 目的 | 命令 / 文件 |
|---|---|
| 解包 APK | `7z x "pudding\research\apps\moondrop link.apk" -o_re\apk` |
| 提取 Dart 字符串 | bundled python + `_re\extract_strings.py` → `_re\libapp.strings.txt` |
| 拉官方云端资源 | bundled python + `_re\fetch_api.py` → `_re\api\*.json`（UA `MoondropApp/1.6.0`） |
| 解析产品清单 | bundled python + `_re\analyze_api.py`（**不要用 pwsh `ConvertFrom-Json`**，会因中文/编码抛 `ArgumentException`） |
| 读 smali 常量 | `Select-String -Path <file>.smali -Pattern 'field|const'` |
| 交叉验证帧 | `_logs2/verbose_*.log` 搜 `GAIA payload:` |

> 工具约束：系统 `python`/`python3` 是 Windows Store 别名（exit 9009），必须用
> `C:\Users\cxh20\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe`；
> 系统 `curl.exe` 因 schannel `SEC_E_NO_CREDENTIALS` 无法出网，需用 `web_fetch` 或 bundled python 的 `urllib`。

---

## 8. 证据索引（关键文件与行号）

**官方 APK（L1）** — 根目录 `pudding/_work/ml_smali/com/qualcomm/qti/gaiaclient/`
- `core/gaia/core/v3/packets/V3Command.smali:7-17,161-225`
- `core/gaia/core/v3/packets/V3PacketType.smali`
- `core/gaia/core/v3/packets/V3Packet.smali:9`（`PAYLOAD_OFFSET=0x4`）、`V3PacketFactory.smali:141-170`（`getSubArray(buf,4)`）、`ResponsePacket.smali:102-115`（`getData()` 直接返回 payload）
- `core/gaia/core/v1v2/packets/V1V2Packet.smali:7-15,195-300`
- `core/gaia/core/version/V2ApiVersion.smali:25-127`、`V2ApiVersionFetcher.smali:82,454-518`
- `core/gaia/qtil/data/QTILFeature.smali:920-1016,1343-1351`
- `core/gaia/qtil/data/GetSupportedFeaturesData.smali:7-15,33-153`
- `core/gaia/qtil/data/battery/BatteryLevel.smali:17,19`
- `core/gaia/qtil/data/audiocuration/Mode.smali:52-259,403-429`、`FeatureState.smali:291-390`
- `core/gaia/qtil/plugins/v3/V3BasicPlugin$COMMANDS.smali`、`V3AncV2Plugin$COMMANDS.smali`、`V3AudioCurationPlugin$COMMANDS.smali`、`V3ANCPlugin$COMMANDS.smali`、`V3BatteryPlugin$COMMANDS.smali`
- `core/gaia/qtil/plugins/v3/V3AudioCurationPlugin.smali:6818,7894-7925`
- `core/gaia/qtil/plugins/v3/V3Plugin.smali:764-880`（`sendPacket` 三个重载）
- `core/gaia/qtil/vendors/QTILV3Vendor.smali:268-1131,2335`（插件注册表）
- `core/bluetooth/client/legatt/LeGattClient.smali:18-88,1113,1171-1199`（Airoha 头）
- `repository/audiocuration/AudioCurationRepositoryImpl.smali`（`setMode` 入口）

**官方 APK（L1）** — Moondrop 自有类 `.../com/moondroplab/moondrop/moondrop_app/native/`
- `handlers/AncV2Handler.smali:23-33,1886-1968,2088-2371`
- `handlers/AncHandler.smali:447-646,648-776,1469-1959`
- `handlers/BluetoothConnectionHandler.smali:1724-2311,1969,2036,5852,5860`
- `handlers/DeviceInfoHandler.smali:443-515,2628,9527-9585`
- `handlers/AirohaHandler.smali:1155,2680,5402`
- `handlers/JieliFotaHandler.smali:2478,5534`
- `ble/source/BleSourceSwitchFrames.smali:17-25,845-934`

**官方云端（L2）** — `_re/api/`
- `products_all.json`（110 款，含 `chipType`/`appResourceRoot`/EQ 静态字段）
- `ota_configs_all.json`（199 条，扩展名 `.fot/.ufw/.hid/.bin`）
- `product_detail_sparxie.json`、`chip_mapping.json`

**实机日志（L3）**
- `_logs2/verbose_2026-11-14T20_55_01.254776.log:22124-22199`（GAIA 握手 + ANC/增益/LED/电量往返）
- `_logs2/modules_2026-11-15T15_52_31.720557.log:13738+`

**HyperDrop（待修）**
- `HyperDrop/app/src/main/java/cythen/hyperdrop/device/protocol/GenericGaiaCommandTable.kt:24-28,39,53,107-112,120-136,179-191`
- `HyperDrop/app/src/main/java/cythen/hyperdrop/device/protocol/PuddingCommandTable.kt:41,49-73,118-138`
- `HyperDrop/app/src/main/java/cythen/hyperdrop/device/protocol/GaiaReport.kt:36`
- `HyperDrop/app/src/main/java/cythen/hyperdrop/device/DeviceModels.kt:13,147-163,167-211`
- `HyperDrop/app/src/main/java/cythen/hyperdrop/device/DeviceProfile.kt:44`
- `HyperDrop/app/src/main/java/cythen/hyperdrop/pods/DeviceSession.kt:587-588,824,1036,1226`
- `HyperDrop/app/src/main/java/cythen/hyperdrop/pods/Packets.kt:315,359-360,414-415`

---

## 9. 修订建议清单（按执行顺序）

1. 修 `GenericGaiaCommandTable.kt:179-191` 二元组解析（D1）—— 唯一会污染运行时数据的确定性 bug。
2. 处理 ANC_V1 分支（D2）—— 删或改，别留着"看起来支持"。
3. 电量解析加 `and 0x7F` 容错（D3），并把 `Packets.kt` 的充电位注释限定为 AA 专用（D5）。
4. 让 `transports` 真正参与连接分派（D4）—— 它现在只是声明，无人读取；连接路径硬编码 SPP。这一步是"适配更多设备"的真正前置条件。
5. 更新 `OFFICIAL_APP_PROTOCOL_REVERSE.md` 与 `CONTROL_PROTOCOL_BY_MODEL.md`：套用本文 A1/A2/A3/A5/A6/A7 的修正，并在结尾统一标注 B1-B4 的限定语。
6. 需要实测才能推进的部分（C1-C4）单独列一个"待抓包"清单，不再在文档里给结论性描述。
