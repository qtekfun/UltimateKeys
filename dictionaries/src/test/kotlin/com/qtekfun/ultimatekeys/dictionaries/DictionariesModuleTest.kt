// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.dictionaries

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DictionariesModuleTest {
    @Test
    fun `module is named after its directory`() {
        assertEquals("dictionaries", DictionariesModule.label(""))
    }
}
