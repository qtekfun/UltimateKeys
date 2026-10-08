// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voice

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private class FakeBackend : WhisperBackend {
    var loadResult = 7L
    var freed = mutableListOf<Long>()
    var aborted = mutableListOf<Long>()
    var pins = mutableListOf<List<Int>>()
    var lastAudio: FloatArray? = null
    var lastLanguages: List<String>? = null
    var lastThreads = 0
    var result: Pair<String, String>? = "en" to " Hello there."
    val entered = CountDownLatch(1)

    /** When set, transcribe blocks until the latch opens or abort is called. */
    var blockUntilAborted = false
    private val release = CountDownLatch(1)

    override fun load(path: String): Long = loadResult

    override fun free(handle: Long) {
        freed += handle
    }

    override fun abort(handle: Long) {
        aborted += handle
        release.countDown()
    }

    override fun transcribe(
        handle: Long,
        audio: FloatArray,
        languages: List<String>,
        threads: Int
    ): Pair<String, String>? {
        lastAudio = audio
        lastLanguages = languages
        lastThreads = threads
        entered.countDown()
        if (blockUntilAborted) {
            release.await(5, TimeUnit.SECONDS)
            return null
        }
        return result
    }

    override fun pinCurrentThread(cpus: IntArray): Boolean {
        pins += cpus.toList()
        return true
    }
}

class WhisperTranscriberTest {
    private val backend = FakeBackend()
    private val logs = mutableListOf<String>()
    private var now = 0L
    private val executor = Executors.newSingleThreadExecutor()
    private val topology = CpuTopology(listOf(4, 5, 6, 7), 4)
    private val transcriber = WhisperTranscriber(
        { topology },
        { logs += it },
        { now.also { now += 50_000_000L } },
        { backend },
        executor
    )

    @AfterEach
    fun tearDown() {
        transcriber.close()
        executor.awaitTermination(2, TimeUnit.SECONDS)
    }

    @Test
    fun `loads, transcribes and unloads`() = runBlocking {
        assertFalse(transcriber.isLoaded)
        transcriber.load("/models/base.bin")
        assertEquals("/models/base.bin", transcriber.loadedModel)
        val result = transcriber.transcribe(FloatArray(32_000), DictationLanguage.AUTO)
        assertEquals(Transcription(" Hello there.", "en"), result)
        assertEquals(listOf("es", "en"), backend.lastLanguages)
        assertEquals(4, backend.lastThreads)
        assertEquals(listOf(listOf(4, 5, 6, 7)), backend.pins)
        assertTrue(logs.any { it.startsWith("transcribed 2000 ms") })
        transcriber.unload()
        assertNull(transcriber.loadedModel)
        assertEquals(listOf(7L), backend.freed)
    }

    @Test
    fun `short audio is padded to what the model accepts`() = runBlocking {
        transcriber.load("m")
        transcriber.transcribe(FloatArray(100), DictationLanguage.SPANISH)
        assertEquals(AudioPrep.samplesFor(AudioPrep.MIN_INPUT_MS), backend.lastAudio?.size)
        assertEquals(listOf("es"), backend.lastLanguages)
    }

    @Test
    fun `threads are pinned once`() = runBlocking {
        transcriber.load("m")
        repeat(3) { transcriber.transcribe(FloatArray(32_000), DictationLanguage.ENGLISH) }
        assertEquals(1, backend.pins.size)
    }

    @Test
    fun `loading again frees the previous model`() = runBlocking {
        transcriber.load("a")
        backend.loadResult = 8L
        transcriber.load("b")
        assertEquals(listOf(7L), backend.freed)
        assertEquals("b", transcriber.loadedModel)
    }

    @Test
    fun `a model that cannot be loaded is reported`(): Unit = runBlocking {
        backend.loadResult = 0L
        assertThrows<ModelLoadException> { transcriber.load("broken") }
        assertFalse(transcriber.isLoaded)
    }

    @Test
    fun `a missing native library is reported as a load failure`(): Unit = runBlocking {
        val missing = WhisperTranscriber(
            { topology },
            null,
            { 0L },
            { throw UnsatisfiedLinkError("no ukwhisper") },
            Executors.newSingleThreadExecutor()
        )
        assertThrows<ModelLoadException> { missing.load("m") }
        missing.close()
    }

    @Test
    fun `transcribing without a model fails`(): Unit = runBlocking {
        assertThrows<TranscriptionException> {
            transcriber.transcribe(FloatArray(20_000), DictationLanguage.AUTO)
        }
    }

    @Test
    fun `an engine failure is reported`(): Unit = runBlocking {
        transcriber.load("m")
        backend.result = null
        assertThrows<TranscriptionException> {
            transcriber.transcribe(FloatArray(20_000), DictationLanguage.AUTO)
        }
    }

    @Test
    fun `cancelling aborts the native call`() = runBlocking {
        transcriber.load("m")
        backend.blockUntilAborted = true
        val job = async(start = CoroutineStart.UNDISPATCHED) {
            transcriber.transcribe(FloatArray(32_000), DictationLanguage.AUTO)
        }
        assertTrue(backend.entered.await(5, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertEquals(listOf(7L), backend.aborted)
    }

    @Test
    fun `close frees the model`() {
        runBlocking { transcriber.load("m") }
        transcriber.close()
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
        assertEquals(listOf(7L), backend.freed)
        assertNull(transcriber.loadedModel)
    }
}
