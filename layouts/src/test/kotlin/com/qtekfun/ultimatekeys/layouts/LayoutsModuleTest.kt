// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.layouts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LayoutsModuleTest {
    @Test
    fun `module is named after its directory`() {
        assertEquals("layouts", LayoutsModule.label(""))
    }
}
