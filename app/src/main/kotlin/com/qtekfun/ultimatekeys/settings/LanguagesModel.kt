// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.settings

import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.languages.Language
import com.qtekfun.ultimatekeys.languages.LanguageCatalog

/** One line of the Languages screen. */
data class LanguageRow(
    val language: Language,
    val enabled: Boolean,
    /** False for the last enabled language and, when the limit is reached, for languages that are off. */
    val canToggle: Boolean,
    /** The enabled regional variant that enabling this language would replace (English UK for US). */
    val replaces: Language?
)

/** The rules of the Languages screen, apart from drawing it. */
object LanguagesModel {
    /** The enabled languages in the order the language key cycles, then the others in catalog order. */
    fun rows(settings: KeyboardSettings): List<LanguageRow> {
        val enabled = settings.enabledLanguages
        val atLimit = enabled.size >= LanguageCatalog.MAX_ENABLED
        val ordered = enabled.mapNotNull { LanguageCatalog.find(it) } +
            LanguageCatalog.all.filter { it.tag !in enabled }
        return ordered.map { language ->
            val on = language.tag in enabled
            val sibling = if (on) null else variantOf(enabled, language)
            LanguageRow(
                language = language,
                enabled = on,
                canToggle = if (on) enabled.size > 1 else sibling != null || !atLimit,
                replaces = sibling
            )
        }
    }

    /** Turns [tag] on or off, keeping the rules: at least one language, at most the limit, one variant each. */
    fun toggle(settings: KeyboardSettings, tag: String, on: Boolean): KeyboardSettings {
        val language = LanguageCatalog.find(tag) ?: return settings
        val enabled = settings.enabledLanguages
        val next = when {
            on && tag in enabled -> enabled

            on -> variantOf(enabled, language)?.let { sibling ->
                enabled.map { if (it == sibling.tag) tag else it }
            } ?: if (enabled.size < LanguageCatalog.MAX_ENABLED) enabled + tag else enabled

            tag !in enabled || enabled.size <= 1 -> enabled

            else -> enabled - tag
        }
        return settings.copy(enabledLanguages = next).sanitized()
    }

    /** The enabled languages that have more than one layout to choose from. */
    fun withLayoutChoice(settings: KeyboardSettings): List<Language> =
        settings.enabledLanguages.mapNotNull { LanguageCatalog.find(it) }
            .filter { it.layoutIds.size > 1 }

    /** Chooses [layoutId] for [tag]; if that language is the one being typed, the keyboard switches at once. */
    fun chooseLayout(settings: KeyboardSettings, tag: String, layoutId: String): KeyboardSettings {
        val language = LanguageCatalog.find(tag)
        if (language == null || layoutId !in language.layoutIds) return settings
        val active = settings.activeLanguage.tag == tag
        return settings.copy(
            languageLayouts = settings.languageLayouts + (tag to layoutId),
            letterLayoutId = if (active) layoutId else settings.letterLayoutId
        ).sanitized()
    }

    private fun variantOf(enabled: List<String>, language: Language): Language? =
        enabled.mapNotNull { LanguageCatalog.find(it) }
            .firstOrNull { it.code == language.code && it.tag != language.tag }

    /** The name of a layout: the arrangement of the keys, which is the same in every language. */
    fun layoutName(layoutId: String): String = when (val kind = layoutId.substringAfterLast('_')) {
        "qwerty" -> "QWERTY"
        "qwertz" -> "QWERTZ"
        "azerty" -> "AZERTY"
        "q" -> "Q"
        "jcuken" -> "ЙЦУКЕН"
        else -> kind.replaceFirstChar { it.uppercase() }
    }
}
