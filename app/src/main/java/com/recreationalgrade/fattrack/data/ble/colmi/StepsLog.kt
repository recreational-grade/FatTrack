package com.recreationalgrade.fattrack.data.ble.colmi

import com.recreationalgrade.fattrack.data.ble.colmi.ColmiResponse.StepsLogPart
import java.time.Instant
import java.time.ZoneOffset

/** Activity in one 15-minute UTC slot. */
data class StepSlot(
    val start: Instant,
    val steps: Int,
    val distanceMeters: Int,
    /**
     * Small calories (1/1000 kcal), after the ×10 for the new protocol.
     * Unit inferred from ~50 cal/step in reference captures; unverified.
     */
    val calories: Int,
)

sealed interface StepsLog {
    data object NoData : StepsLog
    data class Slots(val slots: List<StepSlot>) : StepsLog
}

/**
 * Folds the reply to [ColmiRequests.stepsLog] into a [StepsLog]. One packet per
 * non-empty slot; complete when packetIndex == packetCount - 1.
 * Format per tahnok/colmi_r02_client.
 */
class StepsLogAssembler {

    private var calorieMultiplier = 1
    private val slots = mutableListOf<StepSlot>()

    /** Returns the finished log once complete, otherwise null. */
    fun add(part: StepsLogPart): StepsLog? = when (part) {
        StepsLogPart.NoData -> StepsLog.NoData

        is StepsLogPart.Header -> {
            calorieMultiplier = if (part.newCalorieProtocol) 10 else 1
            null
        }

        is StepsLogPart.Slot -> {
            slots += StepSlot(
                start = part.date.atStartOfDay(ZoneOffset.UTC).toInstant()
                    .plusSeconds(part.slotIndex * SLOT_SECONDS),
                steps = part.steps,
                distanceMeters = part.distanceMeters,
                calories = part.caloriesRaw * calorieMultiplier,
            )
            if (part.packetIndex >= part.packetCount - 1) StepsLog.Slots(slots.toList()) else null
        }
    }

    private companion object {
        const val SLOT_SECONDS = 15L * 60
    }
}
