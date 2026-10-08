// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatekeys.R
import com.qtekfun.ultimatekeys.style.ArgbColor
import com.qtekfun.ultimatekeys.ui.UkRadius
import com.qtekfun.ultimatekeys.ui.UkSpacing
import com.qtekfun.ultimatekeys.ui.UkTextField
import com.qtekfun.ultimatekeys.ui.UkTheme

/**
 * A colour with a swatch and a `#RRGGBB` / `#AARRGGBB` field that only commits valid values. The
 * swatch is decorative (the hex code says the same); an invalid code is flagged in words too.
 */
@Composable
fun ColorRow(label: String, color: ArgbColor, onChange: (ArgbColor) -> Unit) {
    var text by remember(color) { mutableStateOf(color.toHex()) }
    val parsed = ArgbColor.parse(text)
    val shape = RoundedCornerShape(UkRadius.badge)
    Row(
        Modifier.fillMaxWidth().padding(start = UkSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(UkSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(SWATCH)
                .background(Color(color.argb), shape)
                .border(1.dp, UkTheme.colors.separator, shape)
                .clearAndSetSemantics { }
        )
        UkTextField(
            value = text,
            onValueChange = {
                text = it
                ArgbColor.parse(it)?.let(onChange)
            },
            label = label,
            isError = parsed == null,
            supportingText = if (parsed == null) stringResource(R.string.color_invalid) else null,
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
}

private val SWATCH = 32.dp
