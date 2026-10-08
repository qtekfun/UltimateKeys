// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatekeys.ime.BottomRowPlan
import com.qtekfun.ultimatekeys.ime.logic.EnterKind
import com.qtekfun.ultimatekeys.ime.logic.KeyboardState
import com.qtekfun.ultimatekeys.layouts.BottomRow
import com.qtekfun.ultimatekeys.layouts.LayoutRepository
import com.qtekfun.ultimatekeys.style.MicPlacement
import com.qtekfun.ultimatekeys.style.Style
import com.qtekfun.ultimatekeys.style.ToolIcons

/**
 * A non-interactive keyboard drawn with [style]; the same renderer as the real keyboard, so what
 * the editor and the screenshot tests show is what typing looks like.
 */
@Composable
fun KeyboardPreview(
    style: Style,
    dark: Boolean,
    modifier: Modifier = Modifier,
    content: PreviewContent = PreviewContent(),
    labels: SurfaceLabels = PreviewLabels
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val surface = remember(style, dark) { SurfaceStyle.resolve(style, dark) }
    val renderer = remember(context) { SurfaceRenderer(FontCatalog(context.assets)) }
    val layout = remember(content.layoutId, content.numberRow, style.bottomRow) {
        BottomRow.apply(
            LayoutRepository.pages(content.layoutId, content.numberRow).letters,
            BottomRowPlan.slots(style.bottomRow, content.features)
        )
    }
    var widthPx by remember { mutableFloatStateOf(0f) }
    val rowDp = SurfaceSpec.rowDp(content.heightPercent)
    val dimens = with(density) {
        SurfaceDimens(
            gapX = surface.gapXDp.dp.toPx(),
            gapY = surface.gapYDp.dp.toPx(),
            corner = surface.cornerDp.dp.toPx(),
            borderWidth = surface.borderWidthDp.dp.toPx(),
            elevation = surface.elevationDp.dp.toPx(),
            labelSize = (rowDp * LABEL_RATIO * surface.labelSizePercent / PERCENT).dp.toPx(),
            hintSize = HINT_DP.dp.toPx(),
            previewHeight = (rowDp).dp.toPx()
        )
    }
    val geometry = remember(layout, widthPx, style, content, density) {
        SurfaceSpec.geometry(
            layout,
            widthPx,
            density.density,
            content.heightPercent,
            style,
            content.numberRow
        )
    }
    val micInMargin = content.features.voice &&
        style.bottomRow.micPlacement == MicPlacement.BOTTOM_MARGIN
    val height = SurfaceSpec.totalHeightDp(
        layout.rows.size,
        content.heightPercent,
        0,
        style,
        content.numberRow,
        micInMargin
    )
    Box(modifier.fillMaxWidth().background(surface.background)) {
        Canvas(
            Modifier.fillMaxWidth().height(height.dp).onSizeChanged { widthPx = it.width.toFloat() }
        ) {
            renderer.draw(
                this,
                geometry,
                content.state,
                emptyList(),
                surface,
                labels,
                dimens,
                StripState(
                    content.suggestions,
                    content.private,
                    content.showToggle,
                    showMic = content.features.voice &&
                        style.bottomRow.micPlacement == MicPlacement.SUGGESTION_BAR,
                    showTools = content.showToggle &&
                        style.suggestionBar.toolIcons == ToolIcons.SHOWN,
                    marginMic = if (micInMargin) {
                        MarginMic.of(
                            widthPx,
                            geometry.top + geometry.height,
                            height * density.density
                        )
                    } else {
                        null
                    }
                )
            )
        }
    }
}

private val PreviewLabels = SurfaceLabels(
    enter = EnterKind.entries.associateWith { "Go" },
    space = "space",
    symbols = "?123",
    letters = "ABC",
    moreSymbols = "=\\<"
)

private const val LABEL_RATIO = 0.4f
private const val PERCENT = 100f
private const val HINT_DP = 10f
