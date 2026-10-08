// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppVersionTest {
    @Test
    fun `keeps what the package manager reports`() {
        assertEquals(AppVersion("0.7.0", 70099L), AppVersion.of("0.7.0", 70099L))
    }

    @Test
    fun `tolerates a missing name and a negative code`() {
        assertEquals(AppVersion(AppVersion.UNKNOWN, 0L), AppVersion.of(null, -3L))
        assertEquals(AppVersion(AppVersion.UNKNOWN, 5L), AppVersion.of("  ", 5L))
    }
}
