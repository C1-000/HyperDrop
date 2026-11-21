package cythen.hyperdrop.device.protocol

/**
 * GAIA feature IDs, as carried by `GET_SUPPORTED_FEATURES` and by the high bits of a v3
 * `commandValue`.
 *
 * Values come from the official client's `QTILFeature.getValue()` (a switch over the enum's
 * *value* field, not its ordinal). The two differ above `DYBASS`: `AUDIO_FILE_STORAGE` is
 * ordinal 0x1c / value 0x1d, `LR_CHANNEL` 0x1d / 0x1e, `TOUCHV4` 0x1e / 0x1f, and `ANC_V2`
 * 0x1f / **0x20**. Always use the value, never the ordinal — reading the ordinal shifts every
 * feature above 0x1b down by one and is the mistake behind the old "ANC_V2 = 0x1f" note.
 *
 * Wire consequence: a v3 `commandValue` packs `(feature << 9) | (type << 7) | command`. Split
 * into its two big-endian bytes that is `high = (feature << 1) | (type >> 1)` and
 * `low = ((type & 1) << 7) | command`, which is why an ANC_V2 COMMAND for command 3 reads as
 * `00 1D 40 03` (0x20 << 9 = 0x4000) and its RESPONSE reads as `00 1D 41 03`, while the same
 * command on AudioCuration reads as `00 1D 10 03` (0x08 << 9 = 0x1000).
 */
object GaiaFeature {
    const val BASIC: Int = 0x00
    const val EARBUD: Int = 0x01
    const val ANC: Int = 0x02
    const val VOICE_UI: Int = 0x03
    const val DEBUG: Int = 0x04
    const val MUSIC_PROCESSING: Int = 0x05
    const val UPGRADE: Int = 0x06
    const val HANDSET_SERVICE: Int = 0x07
    const val AUDIO_CURATION: Int = 0x08
    const val EARBUD_FIT: Int = 0x09
    const val VOICE_PROCESSING: Int = 0x0A
    const val GESTURE_CONFIGURATION: Int = 0x0B
    const val STATISTICS: Int = 0x0C
    const val BATTERY: Int = 0x0D
    const val VOICE: Int = 0x0E
    const val DAC_GAIN: Int = 0x0F
    const val CODEC_TYPE: Int = 0x10
    const val LIGHT_SENSOR: Int = 0x11
    const val SPATIAL_AUDIO: Int = 0x12
    const val LED: Int = 0x13
    const val ONEBRINGTWO: Int = 0x14
    const val BT_ADDRESS: Int = 0x15
    const val TOUCHV2: Int = 0x16
    const val AUDIO_RESOURCE: Int = 0x17
    const val POWER_CONTROL: Int = 0x18
    const val POWER_TIMEOUT: Int = 0x19
    const val TOUCHV3: Int = 0x1A
    const val DYBASS: Int = 0x1B
    const val AUDIO_FILE_STORAGE: Int = 0x1D
    const val LR_CHANNEL: Int = 0x1E
    const val TOUCHV4: Int = 0x1F
    const val ANC_V2: Int = 0x20
}
