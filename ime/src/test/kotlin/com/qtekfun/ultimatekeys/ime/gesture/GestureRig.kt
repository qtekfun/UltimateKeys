// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.gesture

import com.qtekfun.ultimatekeys.dictionaries.AssetSource
import com.qtekfun.ultimatekeys.dictionaries.DictionaryInstaller
import com.qtekfun.ultimatekeys.dictionaries.DictionaryLocator
import com.qtekfun.ultimatekeys.dictionaries.WordEntry
import com.qtekfun.ultimatekeys.dictionaries.WordListParser
import com.qtekfun.ultimatekeys.gesture.GestureKeyboard
import com.qtekfun.ultimatekeys.gesture.GestureVocabulary
import com.qtekfun.ultimatekeys.ime.surface.SurfaceSpec
import com.qtekfun.ultimatekeys.layouts.LayoutRepository
import java.io.File
import java.nio.file.Files
import java.util.Locale

/** Real layouts and real word lists for the accuracy and latency tests; built once per JVM. */
object GestureRig {
    val spanish: Locale = Locale.forLanguageTag("es")
    val english: Locale = Locale.forLanguageTag("en")

    private val locator: DictionaryLocator by lazy {
        val root =
            File(requireNotNull(System.getProperty("dictionaries.assets")) { "not set by Gradle" })
        val target = Files.createTempDirectory("uk-gesture-dictionaries").toFile().also {
            it.deleteOnExit()
        }
        DictionaryInstaller(
            AssetSource { path ->
                File(root, path).inputStream()
            },
            File(target, "d")
        )
            .ensureInstalled()
    }

    /** The word list of [locale], most frequent first. */
    fun words(locale: Locale): List<WordEntry> =
        locator.fileFor(locale)!!.inputStream().use { WordListParser.parseGzip(it) }.words
            .filter { !it.notAWord && !it.possiblyOffensive }

    /** Spanish first (the primary language), then English, like the keyboard. */
    val vocabulary: GestureVocabulary by lazy {
        GestureSupport.vocabulary(locator, listOf(spanish, english))
    }

    fun keyboard(layoutId: String): GestureKeyboard {
        val geometry = SurfaceSpec.geometry(
            LayoutRepository.load(layoutId),
            widthPx = PHONE_WIDTH_PX,
            density = PHONE_DENSITY,
            heightPercent = 100
        )
        return GestureSupport.keyboard(geometry)
    }

    private const val PHONE_WIDTH_PX = 1080f
    private const val PHONE_DENSITY = 2.75f
}
