// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime

import android.content.pm.ApplicationInfo
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.qtekfun.ultimatekeys.core.settingsRepository
import com.qtekfun.ultimatekeys.core.styleRepository
import com.qtekfun.ultimatekeys.core.userWordsRepository
import com.qtekfun.ultimatekeys.dictionaries.BinaryDictionaries
import com.qtekfun.ultimatekeys.dictionaries.installDictionaries
import com.qtekfun.ultimatekeys.engine.AospSuggestionEngine
import com.qtekfun.ultimatekeys.engine.DictionaryBuilder
import com.qtekfun.ultimatekeys.engine.SharedEngine
import com.qtekfun.ultimatekeys.engine.mixed.MixedSuggestionEngine
import com.qtekfun.ultimatekeys.ime.logic.EditorContext
import com.qtekfun.ultimatekeys.ime.logic.InputLogic
import com.qtekfun.ultimatekeys.ime.surface.KeyboardSurface
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** The keyboard. Hosts a Compose view, which needs the lifecycle owners an IME window lacks. */
class UltimateKeysService :
    InputMethodService(),
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine = SharedEngine.instance
    private lateinit var controller: KeyboardController

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null as Bundle?)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        loadEngineInBackground()
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        controller = KeyboardController(
            logic = InputLogic(),
            repository = settingsRepository(),
            scope = scope,
            feedback = Feedback(this),
            engine = engine,
            styles = styleRepository(),
            showImePicker = {
                getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            },
            logLatency = if (debuggable) { msg -> Log.d("UKLatency", msg) } else null
        )
    }

    /** Builds the dictionaries on first run (seconds) and then switches typing to the real engine. */
    private fun loadEngineInBackground() {
        if (!ENGINE_ENABLED) return
        scope.launch(Dispatchers.IO) {
            try {
                val source = installDictionaries()
                val builder = DictionaryBuilder { Log.w("UltimateKeys", "dictionary build: $it") }
                val locator = BinaryDictionaries(
                    source,
                    File(filesDir, "engine/dictionaries"),
                    builder::build
                )
                    .prepare()
                val aosp = AospSuggestionEngine(File(filesDir, "engine/learned"), locator)
                val spanish = Locale.forLanguageTag("es")
                val english = Locale.forLanguageTag("en")
                engine.swap(
                    MixedSuggestionEngine(
                        mapOf(spanish to aosp, english to aosp),
                        primary = spanish
                    )
                )
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Throwable
            ) {
                // Typing must keep working without suggestions: never let engine problems escape.
                Log.e("UltimateKeys", "Suggestion engine unavailable", e)
            }
        }
    }

    override fun onCreateInputView(): View {
        val owner = this
        window.window?.decorView?.apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
        }
        return ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { KeyboardSurface(controller) }
        }
    }

    /** The system can still call us after onDestroy; a destroyed lifecycle cannot move. */
    private fun moveTo(state: Lifecycle.State) {
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.currentState = state
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        moveTo(Lifecycle.State.RESUMED)
        val ic = currentInputConnection ?: return
        controller.logic.onStartInput(
            AndroidEditorConnection(ic),
            EditorContext.from(info.inputType, info.imeOptions),
            restarting,
            info.initialSelStart
        )
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        controller.logic.onFinishInput()
        moveTo(Lifecycle.State.STARTED)
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd
        )
        controller.logic.onSelectionChanged(newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        scope.cancel()
        engine.close()
        store.clear()
        super.onDestroy()
    }

    private companion object {
        /**
         * Kill switch for the native suggestion engine. A native crash would take the whole keyboard
         * down, so keep the engine behind this flag (see docs/adr/0008).
         */
        const val ENGINE_ENABLED = true
    }
}
