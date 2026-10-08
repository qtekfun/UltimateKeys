// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.languages

import java.util.Locale

/** The writing system of a language; decides the case rules and the look of its layouts. */
enum class Script { LATIN, CYRILLIC, GREEK }

/**
 * One language the keyboard can type.
 *
 * @property tag BCP-47 tag. It is also the key used in the settings and the id of the language's dictionary in
 *   `dictionaries/sources.properties`.
 * @property nativeName the language written in itself, shown in the Languages screen.
 * @property spaceName shorter name drawn on the space bar.
 * @property layoutIds ids of the letter layouts (JSON resources of `:layouts`), the default one first.
 */
data class Language(
    val tag: String,
    val nativeName: String,
    val englishName: String,
    val script: Script,
    val layoutIds: List<String>,
    val spaceName: String = nativeName
) {
    init {
        require(layoutIds.isNotEmpty()) { "$tag needs at least one layout" }
    }

    val locale: Locale get() = Locale.forLanguageTag(tag)

    /** Primary language subtag: `pt` for `pt-BR`. Regional variants of one language exclude each other. */
    val code: String get() = tag.substringBefore('-')

    val defaultLayoutId: String get() = layoutIds.first()

    /** Id of the dictionary of this language (the same as the tag). */
    val dictionaryId: String get() = tag
}

/**
 * Every language the keyboard supports, as data. Adding a language means adding a layout, a dictionary pin and a
 * line here; nothing else in the code names languages (ADR 0022).
 */
object LanguageCatalog {
    /** More enabled languages than this make the suggestion engine and the gesture vocabulary too heavy. */
    const val MAX_ENABLED = 6

    val DEFAULT_ENABLED: List<String> = listOf("es", "en-US")

    private val englishLayouts = listOf("en_qwerty", "en_dvorak", "en_colemak")

    val all: List<Language> = listOf(
        Language("es", "Español", "Spanish", Script.LATIN, listOf("es_qwerty")),
        Language(
            "en-US",
            "English (US)",
            "English (US)",
            Script.LATIN,
            englishLayouts,
            spaceName = "English"
        ),
        Language("en-GB", "English (UK)", "English (UK)", Script.LATIN, englishLayouts),
        Language("fr", "Français", "French", Script.LATIN, listOf("fr_azerty", "fr_qwerty")),
        Language("de", "Deutsch", "German", Script.LATIN, listOf("de_qwertz", "de_qwerty")),
        Language("it", "Italiano", "Italian", Script.LATIN, listOf("it_qwerty")),
        Language(
            "pt-BR",
            "Português (Brasil)",
            "Portuguese (Brazil)",
            Script.LATIN,
            listOf("pt_br_qwerty"),
            spaceName = "Português (BR)"
        ),
        Language(
            "pt-PT",
            "Português (Portugal)",
            "Portuguese (Portugal)",
            Script.LATIN,
            listOf("pt_pt_qwerty"),
            spaceName = "Português (PT)"
        ),
        Language("nl", "Nederlands", "Dutch", Script.LATIN, listOf("nl_qwerty")),
        Language("pl", "Polski", "Polish", Script.LATIN, listOf("pl_qwerty")),
        Language("cs", "Čeština", "Czech", Script.LATIN, listOf("cs_qwertz")),
        Language("da", "Dansk", "Danish", Script.LATIN, listOf("da_qwerty")),
        Language("nb", "Norsk bokmål", "Norwegian Bokmål", Script.LATIN, listOf("nb_qwerty")),
        Language("sv", "Svenska", "Swedish", Script.LATIN, listOf("sv_qwerty")),
        Language("fi", "Suomi", "Finnish", Script.LATIN, listOf("fi_qwerty")),
        Language("tr", "Türkçe", "Turkish", Script.LATIN, listOf("tr_q")),
        Language("ro", "Română", "Romanian", Script.LATIN, listOf("ro_qwerty")),
        Language("hr", "Hrvatski", "Croatian", Script.LATIN, listOf("hr_qwertz")),
        Language("sl", "Slovenščina", "Slovenian", Script.LATIN, listOf("sl_qwertz")),
        Language("sr-Cyrl", "Српски", "Serbian (Cyrillic)", Script.CYRILLIC, listOf("sr_cyrillic")),
        Language("lt", "Lietuvių", "Lithuanian", Script.LATIN, listOf("lt_qwerty")),
        Language("lv", "Latviešu", "Latvian", Script.LATIN, listOf("lv_qwerty")),
        Language("ru", "Русский", "Russian", Script.CYRILLIC, listOf("ru_jcuken")),
        Language("el", "Ελληνικά", "Greek", Script.GREEK, listOf("el_greek"))
    )

    private val byTag: Map<String, Language> = all.associateBy { it.tag }

    fun find(tag: String): Language? = byTag[tag]

    /** The language of [locale]: the exact tag (`en-GB`) first, otherwise the first of its language (`pt`). */
    fun forLocale(locale: Locale): Language? =
        byTag[locale.toLanguageTag()] ?: all.firstOrNull { it.code == locale.language }

    /** Every letter layout id of every language, without repeats. */
    val letterLayoutIds: List<String> = all.flatMap { it.layoutIds }.distinct()

    /**
     * The language whose layout is [layoutId], preferring one of [enabled]. Layouts shared by regional variants
     * (English) resolve to the enabled variant.
     */
    fun languageOfLayout(layoutId: String, enabled: List<String> = emptyList()): Language? {
        val candidates = all.filter { layoutId in it.layoutIds }
        return candidates.firstOrNull { it.tag in enabled } ?: candidates.firstOrNull()
    }

    /** The layout to use for [tag]: the user's choice in [overrides] when valid, else the language default. */
    fun layoutFor(tag: String, overrides: Map<String, String> = emptyMap()): String {
        val language = byTag[tag] ?: return all.first().defaultLayoutId
        return overrides[tag]?.takeIf { it in language.layoutIds } ?: language.defaultLayoutId
    }

    /**
     * Cleans a user-provided list of enabled languages: unknown tags and repeats go, so does a second regional
     * variant of the same language (the first wins), the list is cut to [MAX_ENABLED], and an empty result falls
     * back to [DEFAULT_ENABLED].
     */
    fun sanitizeEnabled(tags: List<String>): List<String> {
        val seenCodes = HashSet<String>()
        val clean = tags.mapNotNull { byTag[it] }.distinct().filter { seenCodes.add(it.code) }
            .take(MAX_ENABLED).map { it.tag }
        return clean.ifEmpty { DEFAULT_ENABLED }
    }

    /** Drops layout choices that name a foreign layout, the default layout, or a language that is not enabled. */
    fun sanitizeLayouts(
        overrides: Map<String, String>,
        enabled: List<String>
    ): Map<String, String> = overrides.filter { (tag, layout) ->
        tag in enabled &&
            byTag[tag]?.let { layout in it.layoutIds && layout != it.defaultLayoutId } == true
    }
}
