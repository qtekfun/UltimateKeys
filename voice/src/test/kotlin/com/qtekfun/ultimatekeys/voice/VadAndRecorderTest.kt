// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voice

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EnergyVadTest {
    private fun EnergyVad.feedFrames(rms: Float, count: Int): VadVerdict {
        var verdict = VadVerdict.CONTINUE
        repeat(count) { verdict = feed(rms, 30) }
        return verdict
    }

    @Test
    fun `silence after speech ends the recording after the timeout`() {
        val vad = EnergyVad(VadConfig(silenceTimeoutMs = 600))
        assertEquals(VadVerdict.CONTINUE, vad.feedFrames(0.0005f, 12))
        assertEquals(VadVerdict.CONTINUE, vad.feedFrames(0.1f, 20))
        assertTrue(vad.heardSpeech)
        assertEquals(VadVerdict.CONTINUE, vad.feedFrames(0.0005f, 15))
        assertEquals(VadVerdict.SILENCE_AFTER_SPEECH, vad.feedFrames(0.0005f, 10))
    }

    @Test
    fun `speech resets the silence`() {
        val vad = EnergyVad(VadConfig(silenceTimeoutMs = 600))
        vad.feedFrames(0.0005f, 12)
        vad.feedFrames(0.1f, 10)
        assertEquals(VadVerdict.CONTINUE, vad.feedFrames(0.0005f, 15))
        assertEquals(VadVerdict.CONTINUE, vad.feedFrames(0.1f, 5))
        assertEquals(VadVerdict.CONTINUE, vad.feedFrames(0.0005f, 15))
    }

    @Test
    fun `no speech at all gives up after its timeout`() {
        val vad = EnergyVad(VadConfig(noSpeechTimeoutMs = 3_000))
        assertEquals(VadVerdict.CONTINUE, vad.feedFrames(0.0005f, 90))
        assertEquals(VadVerdict.NO_SPEECH, vad.feedFrames(0.0005f, 20))
        assertFalse(vad.heardSpeech)
    }

    @Test
    fun `a click is not speech`() {
        val vad = EnergyVad(VadConfig(minSpeechMs = 150))
        vad.feedFrames(0.0005f, 12)
        vad.feedFrames(0.2f, 2)
        assertFalse(vad.heardSpeech)
        vad.feedFrames(0.0005f, 5)
        vad.feedFrames(0.2f, 4)
        assertTrue(vad.heardSpeech)
    }

    @Test
    fun `steady background noise is not speech`() {
        val vad = EnergyVad()
        // A fan at -42 dB: well above digital silence, but the floor follows it.
        vad.feedFrames(0.008f, 40)
        assertFalse(vad.speaking)
        assertFalse(vad.heardSpeech)
        vad.feedFrames(0.1f, 10)
        assertTrue(vad.heardSpeech)
    }

    @Test
    fun `a quiet room still hears ordinary speech`() {
        val vad = EnergyVad()
        vad.feedFrames(0.0f, 12)
        vad.feedFrames(0.03f, 10)
        assertTrue(vad.heardSpeech)
    }

    @Test
    fun `decibels and meter level`() {
        assertEquals(-20f, EnergyVad.decibels(0.1f), 0.01f)
        assertEquals(-90f, EnergyVad.decibels(0f))
        assertEquals(0f, EnergyVad.meterLevel(0f))
        assertEquals(1f, EnergyVad.meterLevel(1f))
        assertTrue(EnergyVad.meterLevel(0.01f) in 0.2f..0.8f)
    }
}

class AudioRecorderTest {
    private val recorder = AudioRecorder(vadConfig = { VadConfig(silenceTimeoutMs = 600) })

    @Test
    fun `stops by itself after the speech ends and trims the silence`() = runTest {
        val frames = repeatFrame(SILENCE_FRAME, 12) + repeatFrame(SPEECH_FRAME, 20)
        val source = ScriptedSource(frames, after = SILENCE_FRAME)
        val levels = mutableListOf<Float>()
        val recording = recorder.record(source, { level, _ -> levels += level }) { false }
        assertEquals(RecordingEnd.SILENCE, recording.end)
        assertTrue(recording.heardSpeech)
        assertTrue(source.started && source.stopped)
        // 12 + 20 frames up to the last word, plus a 400 ms tail; the 600 ms of silence is dropped.
        val expected = AudioPrep.samplesFor(32 * 30 + 400)
        assertEquals(expected, recording.samples.size)
        assertTrue(levels.max() > levels.first())
    }

    @Test
    fun `the person can stop it`() = runTest {
        val source = ScriptedSource(emptyList(), after = SPEECH_FRAME)
        var reads = 0
        val recording = recorder.record(source, { _, _ -> reads++ }) { reads >= 5 }
        assertEquals(RecordingEnd.USER_STOPPED, recording.end)
        assertEquals(AudioPrep.samplesFor(5 * 30), recording.samples.size)
    }

    @Test
    fun `gives up when nothing is said`() = runTest {
        val source = ScriptedSource(emptyList(), after = SILENCE_FRAME)
        val short = AudioRecorder(vadConfig = { VadConfig(noSpeechTimeoutMs = 2_000) })
        val recording = short.record(source, { _, _ -> }) { false }
        assertEquals(RecordingEnd.NO_SPEECH, recording.end)
        assertFalse(recording.heardSpeech)
    }

    @Test
    fun `the length is capped`() = runTest {
        val source = ScriptedSource(emptyList(), after = SPEECH_FRAME)
        val capped = AudioRecorder(maxRecordingMs = 300)
        val recording = capped.record(source, { _, _ -> }) { false }
        assertEquals(RecordingEnd.MAX_LENGTH, recording.end)
        assertEquals(AudioPrep.samplesFor(300), recording.samples.size)
    }

    @Test
    fun `a broken microphone is reported and the source is released`() = runTest {
        val source = ScriptedSource(listOf(SILENCE_FRAME))
        assertThrows<AudioCaptureException> { recorder.record(source, { _, _ -> }) { false } }
        assertTrue(source.stopped)
        val busy = ScriptedSource(emptyList(), failAtStart = true)
        assertThrows<AudioCaptureException> { recorder.record(busy, { _, _ -> }) { false } }
    }

    @Test
    fun `short reads are accepted`() = runTest {
        val source = ScriptedSource(listOf(frame(0.1f, ms = 10)), after = frame(0.1f, ms = 10))
        val recording = recorder.record(source, { _, _ -> }) { false }
        assertEquals(RecordingEnd.MAX_LENGTH, recording.end)
        assertEquals(AudioPrep.samplesFor(AudioRecorder.DEFAULT_MAX_MS), recording.samples.size)
    }
}
