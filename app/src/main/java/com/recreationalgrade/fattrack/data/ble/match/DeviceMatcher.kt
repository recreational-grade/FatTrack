package com.recreationalgrade.fattrack.data.ble.match

/**
 * Recognizes a device family from its advertised name. Name-only for now;
 * widen the input if a family ever needs service UUIDs or manufacturer data.
 */
fun interface DeviceMatcher {
    fun match(name: String): DeviceMatch?
}

object DeviceRecognizer {
    private val matchers: List<DeviceMatcher> = listOf(
        ColmiRingMatcher
        // Phase 3: scale matchers (ported from openScale) go here.
    )

    fun recognize(name: String?): DeviceMatch? =
        name?.let { n -> matchers.firstNotNullOfOrNull { it.match(n) } }
}
