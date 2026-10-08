// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.language

import android.content.Context
import com.qtekfun.ultimatekeys.dictionaries.BinaryDictionaries
import com.qtekfun.ultimatekeys.dictionaries.DictionaryLocator
import com.qtekfun.ultimatekeys.dictionaries.binaryDictionariesDir
import com.qtekfun.ultimatekeys.dictionaries.installDictionaries
import com.qtekfun.ultimatekeys.engine.AospSuggestionEngine
import com.qtekfun.ultimatekeys.engine.DictionaryBuilder
import com.qtekfun.ultimatekeys.engine.SwappableSuggestionEngine
import com.qtekfun.ultimatekeys.engine.mixed.MixedSuggestionEngine
import com.qtekfun.ultimatekeys.ime.gesture.GestureSupport
import com.qtekfun.ultimatekeys.ime.gesture.GestureTyping
import com.qtekfun.ultimatekeys.ime.suggest.LayoutGeometries
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow

/** The process-wide state of the languages, read by the Languages screen (the app and the keyboard share it). */
object SharedLanguageStatus {
    val flow = MutableStateFlow<Map<String, LanguageStatus>>(emptyMap())
}

/**
 * [LanguageHost] over the real parts: it copies the bundled word lists, builds the binary dictionaries with the
 * native builder, owns the one native suggestion engine and gives the settings-driven mixed engine and the gesture
 * vocabulary to the keyboard. Everything here runs on a background thread.
 *
 * @param layoutFor the layout the user chose for a language (tag), which decides the engine's key grid.
 */
class AndroidLanguageHost(
    private val context: Context,
    private val engine: SwappableSuggestionEngine,
    private val gesture: GestureTyping,
    private val layoutFor: (String) -> String,
    private val log: (String) -> Unit = {}
) : LanguageHost {
    private var source: DictionaryLocator? = null
    private var dictionaries: BinaryDictionaries? = null
    private var base: AospSuggestionEngine? = null

    override fun prepare(languages: List<String>): Set<String> {
        val builder = DictionaryBuilder { log("dictionary build: $it") }
        val ready = HashSet<String>()
        for (tag in languages) {
            // One language failing (no space, a broken build) must not take the others down.
            @Suppress("TooGenericExceptionCaught")
            try {
                val installed = context.installDictionaries(setOf(tag))
                source = installed
                val binary = dictionaries ?: BinaryDictionaries(
                    installed,
                    context.binaryDictionariesDir(),
                    build = builder::build
                ).also { dictionaries = it }
                if (tag in binary.prepare(listOf(tag))) ready += tag
            } catch (e: Exception) {
                log("language $tag unavailable: $e")
            }
        }
        return ready
    }

    override fun installEngine(languages: List<Locale>, primary: Locale) {
        val binary = dictionaries ?: return
        val shared = base ?: AospSuggestionEngine(
            File(context.filesDir, "engine/learned"),
            binary.locator(),
            geometryFor = { locale -> LayoutGeometries.forLocale(locale, layoutFor) }
        ).also { base = it }
        engine.swap(
            MixedSuggestionEngine(
                languages.associateWith { shared },
                primary = primary,
                closeEngines = false
            )
        )
    }

    override fun release(languages: List<Locale>, keep: List<String>) {
        base?.let { shared -> languages.forEach(shared::releaseLanguage) }
        dictionaries?.prune(keep)
    }

    override fun installGestures(languages: List<Locale>, primary: Locale) {
        val installed = source ?: return
        gesture.install(GestureSupport.vocabulary(installed, languages), primary.toLanguageTag())
    }

    override fun setGesturePrimary(primary: Locale) = gesture.setPrimary(primary.toLanguageTag())

    /** Closes the native engine; call when the service is destroyed. */
    fun close() {
        base?.close()
        base = null
    }
}
