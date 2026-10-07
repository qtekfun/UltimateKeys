// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import androidx.compose.ui.graphics.Color

/** Temporary fixed look. The style engine (Phase 3) replaces it. */
data class TempStyle(
    val background: Color,
    val letterKey: Color,
    val functionKey: Color,
    val actionKey: Color,
    val pressed: Color,
    val text: Color,
    val hint: Color,
    val popup: Color,
    val popupSelected: Color
) {
    companion object {
        val Dark = TempStyle(
            background = Color(0xFF121214),
            letterKey = Color(0xFF2B2D31),
            functionKey = Color(0xFF1D1E21),
            actionKey = Color(0xFF4F7CFF),
            pressed = Color(0xFF50545C),
            text = Color(0xFFEDEDED),
            hint = Color(0xFF9AA0AA),
            popup = Color(0xFF3A3D43),
            popupSelected = Color(0xFF4F7CFF)
        )
        val Light = TempStyle(
            background = Color(0xFFE6E8EC),
            letterKey = Color(0xFFFFFFFF),
            functionKey = Color(0xFFCDD1D8),
            actionKey = Color(0xFF2F5BFF),
            pressed = Color(0xFFB9BEC8),
            text = Color(0xFF1A1B1E),
            hint = Color(0xFF5E6470),
            popup = Color(0xFFFFFFFF),
            popupSelected = Color(0xFF2F5BFF)
        )
    }
}
