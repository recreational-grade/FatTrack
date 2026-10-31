package com.recreationalgrade.fattrack.data.ble.match

/**
 * Colmi rings advertise names like "COLMI R10_7502": model plus the last two
 * MAC bytes. Some models may omit the "COLMI " prefix (e.g. "R02_1A2B").
 *
 * The Colmi GATT services (6E40FFF0…, DE5BF728…) are not advertised, so the
 * name is all we have at scan time. The connection layer must confirm those
 * services before treating the device as a ring.
 */
object ColmiRingMatcher : DeviceMatcher {

    private val NAME = Regex("""(?:COLMI )?(R\d{2}[A-Z]?)_[0-9A-F]{4}""", RegexOption.IGNORE_CASE)

    private val VERIFIED_MODELS = setOf("R10")

    override fun match(name: String): DeviceMatch? {
        val model = NAME.matchEntire(name)?.groupValues?.get(1)?.uppercase() ?: return null
        return DeviceMatch(
            family = DeviceFamily.COLMI_RING,
            model = "Colmi $model",
            support = if (model in VERIFIED_MODELS) SupportLevel.VERIFIED else SupportLevel.UNTESTED,
        )
    }
}
