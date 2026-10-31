package com.recreationalgrade.fattrack.data.ble.match

import com.recreationalgrade.fattrack.data.ble.cleanDeviceName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColmiRingMatcherTest {

    @Test fun `R10 with prefix is verified`() {
        val match = ColmiRingMatcher.match("COLMI R10_7502")!!
        assertEquals(DeviceFamily.COLMI_RING, match.family)
        assertEquals("Colmi R10", match.model)
        assertEquals(SupportLevel.VERIFIED, match.support)
    }

    @Test fun `other models without prefix are untested`() {
        val match = ColmiRingMatcher.match("R02_1A2B")!!
        assertEquals("Colmi R02", match.model)
        assertEquals(SupportLevel.UNTESTED, match.support)
    }

    @Test fun `matching ignores case`() {
        assertEquals("Colmi R10", ColmiRingMatcher.match("colmi r10_75ab")?.model)
    }

    @Test fun `non-Colmi names do not match`() {
        listOf("COLMI R10", "R10_75021", "Mi Smart Band 8", "RING_7502", "XCOLMI R10_7502")
            .forEach { assertNull(it, ColmiRingMatcher.match(it)) }
    }

    @Test fun `NUL-padded name is cleaned before matching`() {
        val cleaned = "COLMI R10_7502\u0000".cleanDeviceName()
        assertEquals("COLMI R10_7502", cleaned)
        assertEquals(SupportLevel.VERIFIED, DeviceRecognizer.recognize(cleaned)?.support)
    }

    @Test fun `blank or NUL-only names become null`() {
        assertNull("\u0000\u0000".cleanDeviceName())
        assertNull("   ".cleanDeviceName())
        assertNull(DeviceRecognizer.recognize(null))
    }
}
