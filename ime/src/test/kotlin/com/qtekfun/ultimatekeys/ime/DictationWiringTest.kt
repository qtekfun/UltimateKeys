// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime

import android.text.InputType
import com.qtekfun.ultimatekeys.core.FakeSettingsRepository
import com.qtekfun.ultimatekeys.engine.Suggestion
import com.qtekfun.ultimatekeys.ime.logic.EditorContext
import com.qtekfun.ultimatekeys.ime.logic.FakeEditorConnection
import com.qtekfun.ultimatekeys.ime.logic.InputLogic
import com.qtekfun.ultimatekeys.ime.suggest.FakeEngine
import com.qtekfun.ultimatekeys.ime.voice.DictationActions
import com.qtekfun.ultimatekeys.ime.voice.DictationHost
import com.qtekfun.ultimatekeys.ime.voice.PanelAction
import com.qtekfun.ultimatekeys.layouts.KeyAction
import com.qtekfun.ultimatekeys.style.MicPlacement
import com.qtekfun.ultimatekeys.voice.AudioSource
import com.qtekfun.ultimatekeys.voice.DictationConfig
import com.qtekfun.ultimatekeys.voice.DictationController
import com.qtekfun.ultimatekeys.voice.DictationError
import com.qtekfun.ultimatekeys.voice.DictationLanguage
import com.qtekfun.ultimatekeys.voice.DictationState
import com.qtekfun.ultimatekeys.voice.SpeechTranscriber
import com.qtekfun.ultimatekeys.voice.Transcription
import com.qtekfun.ultimatekeys.voice.VadConfig
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ScriptedTranscriber(var text: String = "hola mundo") : SpeechTranscriber {
    override var loadedModel: String? = null
        private set

    override suspend fun load(modelPath: String) {
        loadedModel = modelPath
    }

    override suspend fun unload() {
        loadedModel = null
    }

    override suspend fun transcribe(samples: FloatArray, language: DictationLanguage) =
        Transcription(text, "es")

    override fun close() = Unit
}

internal class RecordingActions : DictationActions {
    val calls = mutableListOf<String>()

    override fun requestMicrophone() {
        calls += "request"
    }

    override fun openAppSettings() {
        calls += "settings"
    }

    override fun openModels() {
        calls += "models"
    }
}

/** A microphone that hears one second of speech, then silence. */
internal class Speech : AudioSource {
    private var reads = 0

    override fun start() = Unit

    override fun read(buffer: FloatArray): Int {
        val loud = reads++ < SPEECH_FRAMES
        buffer.fill(if (loud) 0.1f else 0.0005f)
        return buffer.size
    }

    override fun stop() = Unit

    private companion object {
        const val SPEECH_FRAMES = 20
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DictationWiringTest {
    private val dir = createTempDirectory("ime-models").toFile()
    private val model = File(dir, "ggml-tiny.bin").apply { writeText("x") }
    private val actions = RecordingActions()
    private val permission = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)
    private var granted = true
    private var withModel = true

    @AfterEach
    fun tearDown() {
        dir.deleteRecursively()
    }

    private class Rig(
        val controller: KeyboardController,
        val logic: InputLogic,
        val editor: FakeEditorConnection,
        val engine: FakeEngine,
        val host: DictationHost,
        val settings: FakeSettingsRepository
    )

    private fun TestScope.rig(): Rig {
        val engine = FakeEngine(suggestions = { _, _ -> listOf(Suggestion("hola", 9)) })
        val logic = InputLogic()
        val editor = FakeEditorConnection()
        lateinit var keyboard: KeyboardController
        val host = DictationHost(
            DictationController(
                scope = this,
                transcriber = ScriptedTranscriber(),
                microphone = { Speech() },
                models = { if (withModel) model else null },
                permission = { granted },
                config = { DictationConfig(vad = VadConfig(silenceTimeoutMs = 600)) },
                onResult = { keyboard.onDictationResult(it) },
                recordDispatcher = UnconfinedTestDispatcher(testScheduler)
            ),
            actions,
            permission,
            backgroundScope
        )
        val settings = FakeSettingsRepository()
        keyboard = KeyboardController(
            logic,
            settings,
            backgroundScope,
            null,
            engine,
            StandardTestDispatcher(testScheduler),
            dictation = host
        ) {}
        logic.onStartInput(
            editor,
            EditorContext.from(InputType.TYPE_CLASS_TEXT, 0),
            restarting = false,
            initialCursor = 0
        )
        return Rig(keyboard, logic, editor, engine, host, settings)
    }

    @Test
    fun `turning the microphone off in the settings hides it and ignores the key`() = runTest {
        val rig = rig()
        runCurrent()
        assertTrue(rig.controller.features.voice)
        rig.settings.update { it.copy(dictationEnabled = false) }
        runCurrent()
        assertFalse(rig.controller.features.voice)
        rig.controller.onAction(KeyAction.MIC)
        runCurrent()
        assertEquals(DictationState.Idle, rig.host.state.value)
        rig.settings.update { it.copy(dictationEnabled = true) }
        runCurrent()
        assertTrue(rig.controller.features.voice)
    }

    @Test
    fun `the microphone key appears only when dictation is wired in`() = runTest {
        val rig = rig()
        assertTrue(rig.controller.features.voice)
        val bare =
            KeyboardController(InputLogic(), FakeSettingsRepository(), backgroundScope, null) {}
        assertFalse(bare.features.voice)
        assertNull(bare.dictation)
        bare.onAction(KeyAction.MIC)
        bare.onKeyboardHidden()
    }

    @Test
    fun `the style decides where the microphone is`() = runTest {
        val rig = rig()
        val style = rig.controller.style.value
        val row = com.qtekfun.ultimatekeys.ime.BottomRowPlan.slots(
            style.bottomRow.copy(micPlacement = MicPlacement.BOTTOM_ROW),
            rig.controller.features
        )
        assertTrue(row.any { it == com.qtekfun.ultimatekeys.layouts.BottomSlot.MIC })
        val bar = com.qtekfun.ultimatekeys.ime.BottomRowPlan.slots(
            style.bottomRow.copy(micPlacement = MicPlacement.SUGGESTION_BAR),
            rig.controller.features
        )
        assertFalse(bar.any { it == com.qtekfun.ultimatekeys.layouts.BottomSlot.MIC })
    }

    @Test
    fun `tapping the microphone dictates into the field`() = runTest {
        val rig = rig()
        runCurrent()
        rig.controller.onAction(KeyAction.MIC)
        assertTrue(rig.host.state.value is DictationState.Listening)
        advanceUntilIdle()
        assertEquals(DictationState.Idle, rig.host.state.value)
        assertEquals("Hola mundo", rig.editor.text.toString())
        assertEquals(0, rig.editor.batchDepth)
    }

    @Test
    fun `dictated words feed learning only outside private mode`() = runTest {
        val rig = rig()
        runCurrent()
        rig.controller.onAction(KeyAction.MIC)
        advanceUntilIdle()
        runCurrent()
        assertEquals(setOf("Hola", "mundo"), rig.engine.learned.map { it.first }.toSet())

        val private = rig()
        runCurrent()
        private.controller.privacy.onStartInput(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            0
        )
        assertTrue(private.controller.privacy.isPrivate)
        private.controller.onAction(KeyAction.MIC)
        advanceUntilIdle()
        runCurrent()
        assertEquals("Hola mundo", private.editor.text.toString())
        assertTrue(private.engine.learned.isEmpty())
    }

    @Test
    fun `hiding the keyboard cancels a dictation`() = runTest {
        val rig = rig()
        runCurrent()
        rig.controller.onAction(KeyAction.MIC)
        rig.controller.onKeyboardHidden()
        advanceUntilIdle()
        assertEquals(DictationState.Idle, rig.host.state.value)
        assertEquals("", rig.editor.text.toString())
    }

    @Test
    fun `without permission the panel asks, then points to settings after a refusal`() = runTest {
        granted = false
        val rig = rig()
        runCurrent()
        rig.controller.onAction(KeyAction.MIC)
        val failed = rig.host.state.value as DictationState.Failed
        assertEquals(DictationError.NO_PERMISSION, failed.error)
        assertEquals(PanelAction.ALLOW_MICROPHONE, rig.host.actionFor(failed.error))
        rig.host.perform(PanelAction.ALLOW_MICROPHONE)
        assertEquals(listOf("request"), actions.calls)

        permission.tryEmit(false)
        runCurrent()
        assertTrue(rig.host.permissionRefused.value)
        assertEquals(PanelAction.OPEN_SETTINGS, rig.host.actionFor(failed.error))
        rig.host.perform(PanelAction.OPEN_SETTINGS)
        assertEquals(listOf("request", "settings"), actions.calls)
        assertEquals(DictationState.Idle, rig.host.state.value)
    }

    @Test
    fun `granting the permission resumes the dictation`() = runTest {
        granted = false
        val rig = rig()
        runCurrent()
        rig.controller.onAction(KeyAction.MIC)
        granted = true
        permission.tryEmit(true)
        runCurrent()
        advanceUntilIdle()
        assertFalse(rig.host.permissionRefused.value)
        assertEquals("Hola mundo", rig.editor.text.toString())
    }

    @Test
    fun `an answer after the panel was closed starts nothing`() = runTest {
        val rig = rig()
        runCurrent()
        permission.tryEmit(true)
        runCurrent()
        assertEquals(DictationState.Idle, rig.host.state.value)
    }

    @Test
    fun `without a model the shortcut opens the model screen`() = runTest {
        withModel = false
        val rig = rig()
        runCurrent()
        rig.controller.onAction(KeyAction.MIC)
        val failed = rig.host.state.value as DictationState.Failed
        assertEquals(DictationError.NO_MODEL, failed.error)
        assertEquals(PanelAction.OPEN_MODELS, rig.host.actionFor(failed.error))
        rig.host.perform(PanelAction.OPEN_MODELS)
        assertEquals(listOf("models"), actions.calls)
        assertEquals(DictationState.Idle, rig.host.state.value)
    }

    @Test
    fun `every error offers a way forward and try again restarts`() = runTest {
        val rig = rig()
        runCurrent()
        assertEquals(PanelAction.OPEN_MODELS, rig.host.actionFor(DictationError.MODEL_LOAD_FAILED))
        for (error in listOf(
            DictationError.MIC_UNAVAILABLE,
            DictationError.NO_SPEECH,
            DictationError.TRANSCRIPTION_FAILED
        )) {
            assertEquals(PanelAction.TRY_AGAIN, rig.host.actionFor(error))
        }
        rig.host.perform(PanelAction.TRY_AGAIN)
        assertTrue(rig.host.state.value is DictationState.Listening)
        rig.host.stop()
        advanceUntilIdle()
        assertEquals("Hola mundo", rig.editor.text.toString())
        rig.host.cancel()
        rig.host.dismiss()
    }
}
