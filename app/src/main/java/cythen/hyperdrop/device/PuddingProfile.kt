package cythen.hyperdrop.device

/**
 * Backwards-compatible alias for the data-driven PUDDING record.
 *
 * The real profile now lives in [DeviceModels.pudding]; this object keeps older call sites
 * and tests that reference `PuddingProfile` compiling without change.
 */
object PuddingProfile : DeviceProfile by DeviceModels.pudding
