package com.recreationalgrade.fattrack.data.ble.match

enum class DeviceKind { RING, SCALE }

/** Which protocol driver handles the device. Phase 3 adds scale families. */
enum class DeviceFamily(val kind: DeviceKind) {
    COLMI_RING(DeviceKind.RING),
}

enum class SupportLevel {
    /** Tested on real hardware. */
    VERIFIED,
    /** Same protocol family, expected to work, but not tested. */
    UNTESTED,
}

data class DeviceMatch(
    val family: DeviceFamily,
    /** Human-readable model, e.g. "Colmi R10". */
    val model: String,
    val support: SupportLevel,
)
