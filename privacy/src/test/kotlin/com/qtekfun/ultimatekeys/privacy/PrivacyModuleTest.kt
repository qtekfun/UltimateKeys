// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PrivacyModuleTest {
    @Test
    fun `module is named after its directory`() {
        assertEquals("privacy", PrivacyModule.label(""))
    }
}
