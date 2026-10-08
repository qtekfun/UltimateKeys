// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.suggest

import com.qtekfun.ultimatekeys.engine.KeyboardGeometry
import com.qtekfun.ultimatekeys.languages.LanguageCatalog
import com.qtekfun.ultimatekeys.layouts.LayoutRepository
import com.qtekfun.ultimatekeys.layouts.letterRows
import java.util.Locale

/**
 * The key grid the engine uses to forgive slips on neighbouring keys, taken from the letter layout of a language
 * (AZERTY, Cyrillic, ...) instead of a fixed QWERTY block.
 */
object LayoutGeometries {
    /**
     * The geometry for [locale]: its catalog language's layout as chosen by [layoutFor] (the default layout unless
     * the user picked another), or a plain QWERTY block for a language the catalog does not know.
     */
    fun forLocale(
        locale: Locale,
        layoutFor: (String) -> String = { tag -> LanguageCatalog.layoutFor(tag) }
    ): KeyboardGeometry {
        val language =
            LanguageCatalog.forLocale(locale) ?: return KeyboardGeometry.qwertyFor(locale)
        val rows = LayoutRepository.load(layoutFor(language.tag)).letterRows()
        return if (rows.isEmpty()) {
            KeyboardGeometry.qwertyFor(
                locale
            )
        } else {
            KeyboardGeometry.fromRows(rows)
        }
    }
}
