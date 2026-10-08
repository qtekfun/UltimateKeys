// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.language

import com.qtekfun.ultimatekeys.core.KeyboardSettings
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LanguageEngineManagerTest {
    private class FakeHost : LanguageHost {
        val prepared = mutableListOf<String>()
        val engines = mutableListOf<Pair<List<String>, String>>()
        val released = mutableListOf<Pair<List<String>, List<String>>>()
        val gestures = mutableListOf<Pair<List<String>, String>>()
        val primaries = mutableListOf<String>()
        var unbuildable = emptySet<String>()
        var throwOnPrepare = false
        var throwOnGestures = false

        override fun prepare(languages: List<String>): Set<String> {
            prepared.addAll(languages)
            if (throwOnPrepare) error("disk full")
            return languages.filter { it !in unbuildable }.toSet()
        }

        override fun installEngine(languages: List<Locale>, primary: Locale) {
            engines += languages.map { it.toLanguageTag() } to primary.toLanguageTag()
        }

        override fun release(languages: List<Locale>, keep: List<String>) {
            released += languages.map { it.toLanguageTag() } to keep
        }

        override fun installGestures(languages: List<Locale>, primary: Locale) {
            if (throwOnGestures) error("no memory")
            gestures += languages.map { it.toLanguageTag() } to primary.toLanguageTag()
        }

        override fun setGesturePrimary(primary: Locale) {
            primaries += primary.toLanguageTag()
        }
    }

    private val host = FakeHost()
    private val settings = MutableStateFlow(KeyboardSettings().sanitized())

    private fun TestScope.manager() = LanguageEngineManager(
        backgroundScope,
        StandardTestDispatcher(testScheduler),
        host
    ).also { it.start(settings) }

    private fun change(transform: (KeyboardSettings) -> KeyboardSettings) {
        settings.value = transform(settings.value).sanitized()
    }

    @Test
    fun `the default languages are prepared, installed and given to the gestures`() = runTest {
        val manager = manager()
        runCurrent()

        assertEquals(listOf("es", "en-US"), host.prepared)
        assertEquals(listOf(listOf("es", "en-US") to "es"), host.engines)
        assertEquals(listOf(listOf("es", "en-US") to "es"), host.gestures)
        assertEquals(
            mapOf("es" to LanguageStatus.READY, "en-US" to LanguageStatus.READY),
            manager.status.value
        )
    }

    @Test
    fun `switching the active language only changes the primary`() = runTest {
        manager()
        runCurrent()

        change { it.copy(letterLayoutId = "en_qwerty") }
        runCurrent()

        assertEquals(listOf("es", "en-US"), host.prepared) // nothing built again
        assertEquals(listOf("es", "en-US") to "en-US", host.engines.last())
        assertEquals(1, host.gestures.size) // vocabulary not reloaded
        assertEquals(listOf("en-US"), host.primaries)
    }

    @Test
    fun `enabling a language builds it and reloads the gestures`() = runTest {
        manager()
        runCurrent()

        change { it.copy(enabledLanguages = listOf("es", "en-US", "fr")) }
        runCurrent()

        assertEquals(listOf("es", "en-US", "fr"), host.prepared.takeLast(3))
        assertEquals(listOf("es", "en-US", "fr") to "es", host.engines.last())
        assertEquals(listOf("es", "en-US", "fr") to "es", host.gestures.last())
    }

    @Test
    fun `disabling a language releases it`() = runTest {
        val manager = manager()
        runCurrent()
        change { it.copy(enabledLanguages = listOf("es", "en-US", "fr")) }
        runCurrent()

        change { it.copy(enabledLanguages = listOf("es", "fr")) }
        runCurrent()

        assertEquals(listOf("en-US") to listOf("es", "fr"), host.released.single())
        assertEquals(listOf("es", "fr") to "es", host.engines.last())
        assertEquals(setOf("es", "fr"), manager.status.value.keys)

        // Enabling it again prepares it again, because its dictionary was pruned.
        val before = host.prepared.size
        change { it.copy(enabledLanguages = listOf("es", "fr", "en-US")) }
        runCurrent()
        assertTrue(host.prepared.size > before)
    }

    @Test
    fun `the active language leads the gesture vocabulary`() = runTest {
        change { it.copy(enabledLanguages = listOf("es", "de"), letterLayoutId = "de_qwertz") }
        manager()
        runCurrent()

        assertEquals(listOf("de", "es") to "de", host.gestures.single())
        assertEquals(listOf("es", "de") to "de", host.engines.single())
    }

    @Test
    fun `a language that cannot be built is reported and left out`() = runTest {
        host.unbuildable = setOf("fr")
        change { it.copy(enabledLanguages = listOf("es", "fr")) }
        val manager = manager()
        runCurrent()

        assertEquals(LanguageStatus.FAILED, manager.status.value.getValue("fr"))
        assertEquals(LanguageStatus.READY, manager.status.value.getValue("es"))
        assertEquals(listOf("es") to "es", host.engines.single())

        // Switching language does not retry the build; changing the enabled set does.
        val builds = host.prepared.size
        change { it.copy(letterLayoutId = "fr_azerty") }
        runCurrent()
        assertEquals(builds, host.prepared.size)
        host.unbuildable = emptySet()
        change { it.copy(enabledLanguages = listOf("es", "fr", "de")) }
        runCurrent()
        assertEquals(LanguageStatus.READY, manager.status.value.getValue("fr"))
    }

    @Test
    fun `when nothing can be built the engine is left alone`() = runTest {
        host.throwOnPrepare = true
        val manager = manager()
        runCurrent()

        assertTrue(host.engines.isEmpty())
        assertEquals(
            mapOf("es" to LanguageStatus.FAILED, "en-US" to LanguageStatus.FAILED),
            manager.status.value
        )
    }

    @Test
    fun `gesture typing failing does not stop the engine and is retried`() = runTest {
        host.throwOnGestures = true
        manager()
        runCurrent()
        assertEquals(1, host.engines.size)
        assertTrue(host.gestures.isEmpty())

        host.throwOnGestures = false
        change { it.copy(letterLayoutId = "en_qwerty") }
        runCurrent()
        assertEquals(1, host.gestures.size)
    }

    @Test
    fun `choosing another layout for a language rebuilds its key grid`() = runTest {
        change { it.copy(enabledLanguages = listOf("es", "fr")) }
        manager()
        runCurrent()
        assertTrue(host.released.isEmpty())

        change { it.copy(languageLayouts = mapOf("fr" to "fr_qwerty")) }
        runCurrent()

        assertEquals(listOf("fr") to listOf("es", "fr"), host.released.single())
        assertEquals(1, host.gestures.size)
    }

    @Test
    fun `stopping ends the work`() = runTest {
        val manager = manager()
        runCurrent()
        manager.stop()
        change { it.copy(enabledLanguages = listOf("fr")) }
        runCurrent()
        assertEquals(1, host.engines.size)
    }
}
