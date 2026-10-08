// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voice

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CpuTopologyTest {
    @Test
    fun `picks the fast cores and caps the threads`() {
        val frequencies = listOf(1800L, 1800, 1800, 1800, 2400, 2400, 2800, 3000).map { it * 1000 }
        val topology = CpuTopology.from(frequencies, availableProcessors = 8)
        assertEquals(listOf(4, 5, 6, 7), topology.bigCores)
        assertEquals(4, topology.threads)
    }

    @Test
    fun `a symmetric chip counts every core as fast`() {
        val topology = CpuTopology.from(List(4) { 2_000_000L }, availableProcessors = 4)
        assertEquals(listOf(0, 1, 2, 3), topology.bigCores)
        assertEquals(4, topology.threads)
    }

    @Test
    fun `two fast cores give two threads`() {
        val topology = CpuTopology.from(listOf(1_000_000L, 1_000_000, 2_800_000, 2_800_000), 4)
        assertEquals(listOf(2, 3), topology.bigCores)
        assertEquals(2, topology.threads)
    }

    @Test
    fun `unknown frequencies fall back to half the processors without pinning`() {
        val topology = CpuTopology.from(listOf(0L, 0L, 0L, 0L, 0L, 0L), availableProcessors = 6)
        assertEquals(emptyList<Int>(), topology.bigCores)
        assertEquals(3, topology.threads)
        assertEquals(1, CpuTopology.from(emptyList(), availableProcessors = 1).threads)
    }

    @Test
    fun `reads frequencies from sysfs and tolerates missing files`() {
        val root = createTempDirectory("cpu").toFile()
        try {
            File(root, "cpu0/cpufreq").mkdirs()
            File(root, "cpu0/cpufreq/cpuinfo_max_freq").writeText("1000000\n")
            File(root, "cpu1/cpufreq").mkdirs()
            File(root, "cpu1/cpufreq/cpuinfo_max_freq").writeText("3000000\n")
            val topology = CpuTopology.system(root, availableProcessors = 3)
            assertEquals(listOf(1), topology.bigCores)
            assertEquals(1, topology.threads)
        } finally {
            root.deleteRecursively()
        }
    }
}

class AudioPrepTest {
    @Test
    fun `short audio is padded and long audio is untouched`() {
        val minimum = AudioPrep.samplesFor(AudioPrep.MIN_INPUT_MS)
        assertEquals(minimum, AudioPrep.padToMinimum(FloatArray(100)).size)
        val long = FloatArray(minimum + 5) { 0.5f }
        assertEquals(long.size, AudioPrep.padToMinimum(long).size)
        assertEquals(0.5f, AudioPrep.padToMinimum(long)[0])
    }

    @Test
    fun `padding keeps the audio and adds silence`() {
        val padded = AudioPrep.padToMinimum(floatArrayOf(0.25f, -0.25f))
        assertEquals(0.25f, padded[0])
        assertEquals(0f, padded.last())
    }

    @Test
    fun `rms of a constant signal is its amplitude`() {
        assertEquals(0.5f, AudioPrep.rms(FloatArray(100) { 0.5f }), 1e-6f)
        assertEquals(0f, AudioPrep.rms(FloatArray(0)))
        assertEquals(0.5f, AudioPrep.rms(floatArrayOf(9f, 0.5f, 0.5f), start = 1, count = 2), 1e-6f)
    }
}

class TranscriptCleanerTest {
    @Test
    fun `non speech annotations are removed`() {
        assertEquals("", TranscriptCleaner.clean(" [BLANK_AUDIO]"))
        assertEquals("", TranscriptCleaner.clean("[ Silence ]"))
        assertEquals("", TranscriptCleaner.clean("(música)"))
        assertEquals("", TranscriptCleaner.clean("♪ ♪"))
        assertEquals("hola", TranscriptCleaner.clean("*cough* hola [MUSIC]"))
    }

    @Test
    fun `speech is kept and whitespace is collapsed`() {
        assertEquals("Hello, world.", TranscriptCleaner.clean("  Hello,\n world.  "))
        assertEquals("Call me (maybe)", TranscriptCleaner.clean("Call me (maybe)"))
    }
}

class VadConfigTest {
    @Test
    fun `values are clamped`() {
        val config = VadConfig(silenceTimeoutMs = 1, noSpeechTimeoutMs = 999_999, minSpeechMs = 0)
            .sanitized()
        assertEquals(500, config.silenceTimeoutMs)
        assertEquals(30_000, config.noSpeechTimeoutMs)
        assertEquals(50, config.minSpeechMs)
    }

    @Test
    fun `language options map to codes`() {
        assertEquals(listOf("es", "en"), DictationLanguage.AUTO.codes)
        assertEquals(listOf("es"), DictationLanguage.SPANISH.codes)
        assertEquals(listOf("en"), DictationLanguage.ENGLISH.codes)
        assertTrue(FakeTranscriber().loadedModel == null)
    }
}
