// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.voice

import com.qtekfun.ultimatekeys.voice.DictationController
import com.qtekfun.ultimatekeys.voice.DictationError
import com.qtekfun.ultimatekeys.voice.DictationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** What the voice panel can send the person to; implemented where an Android context exists. */
interface DictationActions {
    /** Asks for the microphone permission (the answer arrives through the permission results). */
    fun requestMicrophone()

    /** Opens the app's system settings, where the permission can be granted by hand. */
    fun openAppSettings()

    /** Opens the model manager (or the closest thing there is) so a model can be chosen. */
    fun openModels()
}

/** The button an error state offers. */
enum class PanelAction { ALLOW_MICROPHONE, OPEN_SETTINGS, OPEN_MODELS, TRY_AGAIN }

/**
 * Connects the dictation controller to the keyboard: the panel's buttons, the permission answer and
 * the error shortcuts. Main thread only.
 */
class DictationHost(
    private val controller: DictationController,
    private val actions: DictationActions,
    permissionResults: Flow<Boolean>,
    scope: CoroutineScope
) {
    val state: StateFlow<DictationState> get() = controller.state

    private val mutableRefused = MutableStateFlow(false)

    /** The person refused the permission in this session, so asking again would do nothing. */
    val permissionRefused: StateFlow<Boolean> = mutableRefused.asStateFlow()

    init {
        permissionResults.onEach(::onPermissionAnswer).launchIn(scope)
    }

    private fun onPermissionAnswer(granted: Boolean) {
        mutableRefused.value = !granted
        val current = state.value
        if (granted && current is DictationState.Failed &&
            current.error == DictationError.NO_PERMISSION
        ) {
            controller.dismiss()
            controller.start()
        }
    }

    /** The microphone key: opens the panel and starts listening. */
    fun open() = controller.start()

    /** Loads the model ahead of time; used while the permission prompt is shown. */
    fun warmUp() = controller.warmUp()

    /** The system is short of memory: free the model if no dictation is running. */
    fun trimMemory() = controller.trimMemory()

    /** The keyboard is going away. */
    fun release() = controller.release()

    /** Ends the recording now and transcribes it. */
    fun stop() = controller.stop()

    /** Throws the audio away and closes the panel. */
    fun cancel() = controller.cancel()

    /** Closes an error state. */
    fun dismiss() = controller.dismiss()

    /** The button that fixes [error]; [refused] is [permissionRefused] as the caller observed it. */
    fun actionFor(error: DictationError, refused: Boolean = mutableRefused.value): PanelAction =
        when (error) {
            DictationError.NO_PERMISSION ->
                if (refused) PanelAction.OPEN_SETTINGS else PanelAction.ALLOW_MICROPHONE

            DictationError.NO_MODEL, DictationError.MODEL_LOAD_FAILED -> PanelAction.OPEN_MODELS

            DictationError.MIC_UNAVAILABLE,
            DictationError.NO_SPEECH,
            DictationError.TRANSCRIPTION_FAILED -> PanelAction.TRY_AGAIN
        }

    fun perform(action: PanelAction) {
        when (action) {
            PanelAction.ALLOW_MICROPHONE -> {
                // The model loads while the person answers the system prompt.
                controller.warmUp()
                actions.requestMicrophone()
            }

            PanelAction.OPEN_SETTINGS -> {
                controller.dismiss()
                actions.openAppSettings()
            }

            PanelAction.OPEN_MODELS -> {
                controller.dismiss()
                actions.openModels()
            }

            PanelAction.TRY_AGAIN -> {
                controller.dismiss()
                controller.start()
            }
        }
    }
}
