// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class VersionCodeTest {
    @Test
    fun `final release uses N 99`() {
        assertEquals(1000099, versionCodeOf("1.0.0"))
        assertEquals(10099, versionCodeOf("0.1.0"))
        assertEquals(1010099, versionCodeOf("1.1.0"))
    }

    @Test
    fun `release candidates sort before the final version`() {
        assertEquals(1000001, versionCodeOf("1.0.0-rc.1"))
        assertEquals(1000002, versionCodeOf("1.0.0-rc.2"))
        assert(versionCodeOf("1.0.0-rc.2") < versionCodeOf("1.0.0"))
    }

    @Test
    fun `rejects malformed or out of range versions`() {
        assertThrows(IllegalStateException::class.java) { versionCodeOf("1.0") }
        assertThrows(IllegalStateException::class.java) { versionCodeOf("1.0.0-beta") }
        assertThrows(IllegalArgumentException::class.java) { versionCodeOf("1.100.0") }
        assertThrows(IllegalArgumentException::class.java) { versionCodeOf("1.0.0-rc.99") }
    }
}
