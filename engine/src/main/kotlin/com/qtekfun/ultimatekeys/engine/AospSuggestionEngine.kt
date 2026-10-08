// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.engine

import java.io.File
import java.util.Locale

/** A main dictionary in AOSP binary format: [length] bytes at [offset] of the file [path]. */
data class DictionaryFile(
    val path: String,
    val offset: Long,
    val length: Long,
    /** True when [path] is a directory written by [DictionaryBuilder] (opened as updatable). */
    val isDirectory: Boolean = false
) {
    companion object {
        /** The whole of [file]. */
        fun of(file: File) = DictionaryFile(file.absolutePath, 0L, file.length())
    }
}

/** Where the read-only main dictionaries live. Called on the engine's background thread. */
fun interface DictionaryLocator {
    /** The main dictionary for [locale] (matched by language), or `null` when there is none. */
    fun mainDictionary(locale: Locale): DictionaryFile?
}

/**
 * [SuggestionEngine] backed by the vendored AOSP LatinIME native engine.
 *
 * Per language it combines three dictionaries: the read-only main dictionary from [dictionaries],
 * an updatable history dictionary that learns words and word pairs as the user types, and an
 * updatable user dictionary holding words the user added explicitly. Both updatable ones live in
 * [storageDir] and are opened lazily on first use.
 *
 * ### Threading
 * Every call can block for milliseconds (memory-mapping a dictionary, walking the trie, writing to
 * disk), so **never call this on the main thread**; use one background dispatcher (for example
 * `Dispatchers.Default.limitedParallelism(1)`) owned by the keyboard. As a safety net all public
 * methods are serialized on an internal lock, so a stray concurrent call waits instead of corrupting
 * native state, but it will not make a main-thread call cheap. After [close] every call is a no-op
 * returning an empty result, so a late call during service shutdown cannot crash.
 *
 * @param storageDir private directory for the learned data; deleted by [clearLearned].
 * @param geometryFor key positions used for typo correction; a plain QWERTY block by default.
 * @param learningEnabled consulted before every [learn]; wire it to private mode.
 * @param autoCorrectThreshold minimum normalized score (0..1) for [Suggestion.autoCorrect]; lower
 *   is more aggressive.
 */
@Suppress("TooManyFunctions", "LongParameterList")
class AospSuggestionEngine internal constructor(
    private val storageDir: File,
    private val dictionaries: DictionaryLocator,
    private val geometryFor: (Locale) -> KeyboardGeometry,
    private val learningEnabled: () -> Boolean,
    private val autoCorrectThreshold: Float,
    private val clockMillis: () -> Long,
    private val bridge: NativeBridge
) : SuggestionEngine {
    constructor(
        storageDir: File,
        dictionaries: DictionaryLocator,
        geometryFor: (Locale) -> KeyboardGeometry = KeyboardGeometry::qwertyFor,
        learningEnabled: () -> Boolean = { true },
        autoCorrectThreshold: Float = DEFAULT_AUTO_CORRECT_THRESHOLD
    ) : this(
        storageDir,
        dictionaries,
        geometryFor,
        learningEnabled,
        autoCorrectThreshold,
        System::currentTimeMillis,
        JniNativeBridge
    )

    private class Opened(val handle: Long, val session: Long, val directory: File?)

    private class LanguageState(
        val proximityInfo: Long,
        val main: Opened?,
        val history: Opened?,
        val user: Opened?
    ) {
        val all get() = listOfNotNull(main, history, user)
    }

    private val lock = Any()
    private val states = HashMap<String, LanguageState>()
    private var closed = false
    private var unflushedLearns = 0

    override fun suggest(
        context: List<String>,
        composing: String,
        locale: Locale
    ): List<Suggestion> = synchronized(lock) {
        if (closed || composing.isEmpty()) return emptyList()
        val state = stateFor(locale)
        val merged = query(state, composing, context)
        val typedIsKnown = merged.any { it.word.equals(composing, ignoreCase = true) } ||
            isKnown(state, composing)
        val ranked = merged.sortedWith(
            compareByDescending<RawSuggestion> {
                it.score
            }.thenBy { it.word.length }
        )
        ranked.mapIndexed { index, raw ->
            val word = matchCase(composing, raw.word)
            val autoCorrect = index == 0 && !typedIsKnown && shouldAutoCorrect(composing, raw)
            Suggestion(word, raw.score, autoCorrect, locale)
        }
    }

    override fun predictNext(context: List<String>, locale: Locale): List<Suggestion> =
        synchronized(lock) {
            if (closed) return emptyList()
            query(stateFor(locale), "", context)
                .sortedByDescending { it.score }
                .map { Suggestion(it.word, it.score, autoCorrect = false, locale = locale) }
        }

    override fun isValidWord(word: String, locale: Locale): Boolean = synchronized(lock) {
        !closed && word.isNotEmpty() && isKnown(stateFor(locale), word)
    }

    override fun learn(word: String, context: List<String>, locale: Locale) {
        synchronized(lock) { learnLocked(word, context, locale) }
    }

    override fun addToUserDictionary(word: String, locale: Locale) {
        synchronized(lock) {
            val clean = word.trim()
            val user = if (closed || !isLearnable(clean)) null else stateFor(locale).user
            if (user != null &&
                bridge.addUnigram(user.handle, clean, USER_WORD_PROBABILITY, nowSeconds())
            ) {
                flush(user)
            }
        }
    }

    override fun removeFromUserDictionary(word: String, locale: Locale) {
        synchronized(lock) {
            val user = if (closed) null else stateFor(locale).user
            if (user != null && bridge.removeUnigram(user.handle, word.trim())) flush(user)
        }
    }

    override fun userDictionaryWords(locale: Locale): List<String> = synchronized(lock) {
        val user = if (closed) null else stateFor(locale).user
        user?.let {
            bridge.words(it.handle).distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        }.orEmpty()
    }

    override fun clearLearned() {
        synchronized(lock) {
            if (!closed) {
                releaseAll(flush = false)
                storageDir.listFiles { file ->
                    file.name.startsWith(HISTORY_PREFIX) || file.name.startsWith(USER_PREFIX)
                }?.forEach { it.deleteRecursively() }
                unflushedLearns = 0
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            if (!closed) {
                releaseAll(flush = true)
                closed = true
            }
        }
    }

    private fun learnLocked(word: String, context: List<String>, locale: Locale) {
        if (closed || !learningEnabled() || !isLearnable(word)) return
        val state = stateFor(locale)
        val history = state.history ?: return
        val known = isKnown(state, word)
        val recent = context.takeLast(MAX_CONTEXT_WORDS)
        if (bridge.learnWord(history.handle, recent, word, known, nowSeconds())) {
            unflushedLearns++
            if (unflushedLearns >= FLUSH_EVERY_LEARNS) flushAll()
        }
    }

    private fun stateFor(locale: Locale): LanguageState {
        val language = languageKey(locale)
        return states.getOrPut(language) {
            val main = dictionaries.mainDictionary(locale)?.let { file ->
                open(
                    file.path,
                    file.offset,
                    file.length,
                    locale,
                    directory = null,
                    updatable = file.isDirectory
                )
            }
            LanguageState(
                proximityInfo = bridge.newProximityInfo(geometryFor(locale)),
                main = main,
                history = openUpdatable(HISTORY_PREFIX, language, historyAttributes(language)),
                user = openUpdatable(USER_PREFIX, language, userAttributes(language))
            )
        }
    }

    private fun open(
        path: String,
        offset: Long,
        length: Long,
        locale: Locale,
        directory: File?,
        updatable: Boolean = directory != null
    ): Opened? {
        val handle = bridge.openDictionary(path, offset, length, updatable = updatable)
        if (handle == 0L) return null
        val session = bridge.newSession(locale.toString(), length)
        return Opened(handle, session, directory)
    }

    /** Opens the updatable dictionary in `storageDir/<prefix><language>`, recreating it when broken. */
    private fun openUpdatable(
        prefix: String,
        language: String,
        attributes: Map<String, String>
    ): Opened? {
        val directory = File(storageDir, prefix + language)
        val locale = Locale.forLanguageTag(language)
        if (directory.isDirectory) {
            open(directory.absolutePath, 0L, 0L, locale, directory)?.let { return it }
            directory.deleteRecursively()
        }
        if (!directory.mkdirs() ||
            !bridge.createEmptyDictionary(directory.absolutePath, language, attributes)
        ) {
            directory.deleteRecursively()
            return null
        }
        return open(directory.absolutePath, 0L, 0L, locale, directory)
    }

    private fun query(
        state: LanguageState,
        composing: String,
        context: List<String>
    ): List<RawSuggestion> {
        val recent = context.takeLast(MAX_CONTEXT_WORDS)
        val best = HashMap<String, RawSuggestion>()
        for (dictionary in state.all) {
            val results = bridge.suggest(
                dictionary.handle,
                dictionary.session,
                state.proximityInfo,
                composing,
                recent
            )
            for (result in results) {
                val key = result.word.lowercase(Locale.ROOT)
                val previous = best[key]
                if (previous == null || result.score > previous.score) best[key] = result
            }
        }
        return best.values.toList()
    }

    private fun isKnown(state: LanguageState, word: String): Boolean {
        val variants = if (word ==
            word.lowercase()
        ) {
            listOf(word)
        } else {
            listOf(word, word.lowercase())
        }
        return state.all.any { dictionary ->
            variants.any {
                bridge.probability(dictionary.handle, it) !=
                    NativeBridge.NOT_A_PROBABILITY
            }
        }
    }

    private fun shouldAutoCorrect(composing: String, candidate: RawSuggestion): Boolean =
        composing.length >= MIN_AUTO_CORRECT_LENGTH &&
            candidate.isAppropriateForAutoCorrection &&
            candidate.baseKind != RawSuggestion.KIND_PREDICTION &&
            bridge.normalizedScore(composing, candidate.word, candidate.score) >=
            autoCorrectThreshold

    private fun flush(opened: Opened) {
        val directory = opened.directory ?: return
        bridge.flush(opened.handle, directory.absolutePath)
    }

    private fun flushAll() {
        states.values.forEach { state -> state.all.forEach(::flush) }
        unflushedLearns = 0
    }

    private fun releaseAll(flush: Boolean) {
        if (flush) flushAll()
        for (state in states.values) {
            state.all.forEach {
                bridge.releaseSession(it.session)
                bridge.closeDictionary(it.handle)
            }
            bridge.releaseProximityInfo(state.proximityInfo)
        }
        states.clear()
    }

    private fun nowSeconds(): Int = (clockMillis() / MILLIS_PER_SECOND).toInt()

    private fun isLearnable(word: String): Boolean = word.isNotEmpty() &&
        word.codePointCount(0, word.length) <= MAX_WORD_CODE_POINTS &&
        word.none { it.isWhitespace() } &&
        word.any { it.isLetter() }

    private fun languageKey(locale: Locale): String = locale.language.ifEmpty { "und" }

    private fun historyAttributes(language: String) =
        mapOf("dictionary" to "history.$language", "HAS_HISTORICAL_INFO" to "1")

    private fun userAttributes(language: String) = mapOf("dictionary" to "user.$language")

    /** The engine works on lower case; give the candidate the capitalization the user typed. */
    private fun matchCase(typed: String, candidate: String): String = when {
        typed.length > 1 && typed.all {
            !it.isLetter() || it.isUpperCase()
        } -> candidate.uppercase()

        typed.first().isUpperCase() -> candidate.replaceFirstChar { it.titlecase() }

        else -> candidate
    }

    companion object {
        /** Same default as the "modest" setting of the original engine. */
        const val DEFAULT_AUTO_CORRECT_THRESHOLD = 0.185f

        private const val HISTORY_PREFIX = "history-"
        private const val USER_PREFIX = "user-"
        private const val MAX_CONTEXT_WORDS = 3
        private const val MAX_WORD_CODE_POINTS = 48
        private const val MIN_AUTO_CORRECT_LENGTH = 2
        private const val USER_WORD_PROBABILITY = 200
        private const val FLUSH_EVERY_LEARNS = 10
        private const val MILLIS_PER_SECOND = 1000L
    }
}
