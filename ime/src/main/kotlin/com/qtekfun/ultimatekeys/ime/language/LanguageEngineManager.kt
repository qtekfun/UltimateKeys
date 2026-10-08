// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.language

import com.qtekfun.ultimatekeys.core.KeyboardSettings
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Where a language stands on its way to being usable for suggestions. */
enum class LanguageStatus {
    /** Its dictionary is being copied and built in the background (seconds, the first time only). */
    PREPARING,

    /** Suggestions in this language work. */
    READY,

    /** The dictionary could not be built; typing works but this language gives no suggestions. */
    FAILED
}

/**
 * The slow, native or Android parts that the manager drives. The service implements it over the dictionary
 * installer, the dictionary builder and the suggestion engine; tests use a fake.
 */
interface LanguageHost {
    /** Installs and builds the dictionaries of [languages]; returns the tags that are ready. Blocking. */
    fun prepare(languages: List<String>): Set<String>

    /** Puts a mixed engine over [languages] (all ready, [primary] among them) in service. */
    fun installEngine(languages: List<Locale>, primary: Locale)

    /** Frees what a language that is no longer enabled holds in memory and on disk. */
    fun release(languages: List<Locale>, keep: List<String>)

    /** Reads the word lists of [languages] into the gesture vocabulary and installs it. Blocking. */
    fun installGestures(languages: List<Locale>, primary: Locale)

    /** Tells gesture typing which language is active, without reloading anything. */
    fun setGesturePrimary(primary: Locale)
}

/**
 * Keeps the suggestion engine and the gesture vocabulary in step with the enabled and active languages (ADR 0022).
 *
 * When the set of enabled languages changes, the dictionaries of the new ones are built in the background (never on
 * the main thread, and only for enabled languages), then a new mixed engine replaces the old one; the vocabulary of
 * the gestures is reloaded after that. When only the active language changes, the engine is re-created with the
 * new primary language and the decoder is told (both are cheap). Languages that were switched off give back their
 * native memory and disk space.
 *
 * Work is serialized on [dispatcher]; if the settings change again while a build runs, only the latest wish is
 * applied afterwards.
 */
class LanguageEngineManager(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val host: LanguageHost,
    private val mutableStatus: MutableStateFlow<Map<String, LanguageStatus>> =
        MutableStateFlow(emptyMap()),
    /** Told about a failure that is otherwise absorbed (typing must go on without suggestions). */
    private val onError: (String, Throwable) -> Unit = { _, _ -> }
) {
    private data class Wish(
        val enabled: List<String>,
        val active: String,
        /** The layout of each enabled language: the engine's typo grid follows it. */
        val layouts: Map<String, String>
    )

    /** The state of every enabled language, for the Languages screen. */
    val status: StateFlow<Map<String, LanguageStatus>> = mutableStatus.asStateFlow()

    private var job: Job? = null
    private var lastEnabled: List<String> = emptyList()
    private val prepared = HashSet<String>()
    private val failed = HashSet<String>()
    private var engineLanguages: List<String> = emptyList()
    private var engineLayouts: Map<String, String> = emptyMap()
    private var gestureLanguages: Set<String> = emptySet()

    /** Starts following [settings]. Call once. */
    fun start(settings: Flow<KeyboardSettings>) {
        job = scope.launch(dispatcher) {
            settings.map { s ->
                Wish(
                    s.enabledLanguages,
                    s.activeLanguage.tag,
                    s.enabledLanguages.associateWith { s.layoutOf(it) }
                )
            }
                .distinctUntilChanged()
                .conflate()
                .collect { apply(it) }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private fun locale(tag: String): Locale = Locale.forLanguageTag(tag)

    private fun publish(enabled: List<String>) {
        mutableStatus.value = enabled.associateWith {
            when (it) {
                in prepared -> LanguageStatus.READY
                in failed -> LanguageStatus.FAILED
                else -> LanguageStatus.PREPARING
            }
        }
    }

    private fun apply(wish: Wish) {
        val enabled = wish.enabled
        if (enabled != lastEnabled) {
            failed.clear()
            lastEnabled = enabled
        }
        publish(enabled)
        if (enabled.any { it !in prepared && it !in failed }) prepare(enabled)
        val usable = enabled.filter { it in prepared }
        if (usable.isEmpty()) return
        val primary = wish.active.takeIf { it in usable } ?: usable.first()
        // A language whose layout changed has a different key grid: let the engine rebuild it.
        val regrid = usable.filter {
            it in engineLanguages && engineLayouts[it] != wish.layouts[it]
        }
        if (regrid.isNotEmpty()) host.release(regrid.map(::locale), usable)
        engineLayouts = wish.layouts
        host.installEngine(usable.map(::locale), locale(primary))
        val dropped = engineLanguages.filter { it !in usable }
        engineLanguages = usable
        if (dropped.isNotEmpty()) {
            prepared -= dropped.toSet()
            host.release(dropped.map(::locale), usable)
        }
        gestures(usable, primary)
    }

    private fun prepare(enabled: List<String>) {
        val ready = try {
            host.prepare(enabled)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            onError("languages unavailable", e)
            emptySet()
        }
        prepared += ready
        failed += enabled.filter { it !in ready }
        publish(enabled)
    }

    private fun gestures(usable: List<String>, primary: String) {
        if (gestureLanguages == usable.toSet()) {
            host.setGesturePrimary(locale(primary))
            return
        }
        gestureLanguages = try {
            host.installGestures(
                (listOf(primary) + (usable - primary)).map(::locale),
                locale(primary)
            )
            usable.toSet()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Gesture typing is optional: typing must keep working without it.
            onError("gesture typing unavailable", e)
            emptySet()
        }
    }
}
