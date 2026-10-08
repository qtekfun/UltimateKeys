// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import android.content.res.AssetManager
import android.graphics.Typeface
import com.qtekfun.ultimatekeys.style.FontChoice

/** Gives the renderer a typeface for a font choice and weight. */
fun interface FontProvider {
    fun typeface(font: FontChoice, weight: Int): Typeface
}

/** The device default font only. */
object SystemFonts : FontProvider {
    override fun typeface(font: FontChoice, weight: Int): Typeface =
        Typeface.create(Typeface.DEFAULT, weight, false)
}

/**
 * Bundled open-licensed fonts (SIL OFL 1.1, see `docs/THIRD_PARTY.md`), loaded from `assets/fonts`.
 * Variable fonts are asked for the exact weight; the static one picks its nearest face. Any loading
 * problem falls back to the system font, so a bad font file can never break typing.
 */
class FontCatalog(private val assets: AssetManager) : FontProvider {
    private val cache = HashMap<Pair<FontChoice, Int>, Typeface>()

    override fun typeface(font: FontChoice, weight: Int): Typeface =
        cache.getOrPut(font to weight) { load(font, weight) ?: SystemFonts.typeface(font, weight) }

    private fun load(font: FontChoice, weight: Int): Typeface? = try {
        when (font) {
            FontChoice.SYSTEM -> null

            FontChoice.INTER -> variable("Inter-Variable.ttf", weight)

            FontChoice.ROBOTO_FLEX -> variable("RobotoFlex-Variable.ttf", weight)

            FontChoice.NUNITO -> variable("Nunito-Variable.ttf", weight)

            FontChoice.ATKINSON_HYPERLEGIBLE -> {
                val face = if (weight >= BOLD_FROM) "Bold" else "Regular"
                Typeface.createFromAsset(assets, "$DIR/AtkinsonHyperlegible-$face.ttf")
            }
        }
    } catch (_: java.io.IOException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    private fun variable(file: String, weight: Int): Typeface? =
        Typeface.Builder(assets, "$DIR/$file")
            .setFontVariationSettings("'wght' $weight")
            .build()

    private companion object {
        const val DIR = "fonts"
        const val BOLD_FROM = 600
    }
}
