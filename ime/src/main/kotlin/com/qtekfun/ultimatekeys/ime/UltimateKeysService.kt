// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
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
import com.qtekfun.ultimatekeys.clipboard.SystemClipboardWatcher
import com.qtekfun.ultimatekeys.clipboard.clipStore
import com.qtekfun.ultimatekeys.core.settingsRepository
import com.qtekfun.ultimatekeys.core.styleRepository
import com.qtekfun.ultimatekeys.core.userWordsRepository
import com.qtekfun.ultimatekeys.emoji.loadEmojiData
import com.qtekfun.ultimatekeys.engine.SharedEngine
import com.qtekfun.ultimatekeys.ime.language.AndroidLanguageHost
import com.qtekfun.ultimatekeys.ime.language.LanguageEngineManager
import com.qtekfun.ultimatekeys.ime.language.SharedLanguageStatus
import com.qtekfun.ultimatekeys.ime.logic.EditorContext
import com.qtekfun.ultimatekeys.ime.logic.InputLogic
import com.qtekfun.ultimatekeys.ime.surface.FirstShowTimer
import com.qtekfun.ultimatekeys.ime.surface.KeyboardSurface
import com.qtekfun.ultimatekeys.ime.voice.AndroidDictationActions
import com.qtekfun.ultimatekeys.ime.voice.DictationHost
import com.qtekfun.ultimatekeys.ime.voice.toDictationConfig
import com.qtekfun.ultimatekeys.voice.DictationController
import com.qtekfun.ultimatekeys.voice.MicrophonePermission
import com.qtekfun.ultimatekeys.voice.MicrophonePermissionFlow
import com.qtekfun.ultimatekeys.voice.MicrophoneSource
import com.qtekfun.ultimatekeys.voice.WhisperTranscriber
import com.qtekfun.ultimatekeys.voicemodels.SelectedModelSource
import com.qtekfun.ultimatekeys.voicemodels.modelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

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

    /** Debug builds only: logs how long the first show took. */
    private var firstShow: FirstShowTimer? = null
    private lateinit var transcriber: WhisperTranscriber
    private var clipboardWatcher: SystemClipboardWatcher? = null
    private var languageHost: AndroidLanguageHost? = null
    private var languages: LanguageEngineManager? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null as Bundle?)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) {
            firstShow = FirstShowTimer(System::nanoTime) { Log.d("UKLatency", it) }
            firstShow?.markCreated()
        }
        transcriber = WhisperTranscriber(
            log = if (debuggable) { msg -> Log.d("UKVoice", msg) } else null
        )
        val dictation = DictationHost(
            controller = DictationController(
                scope = scope,
                transcriber = transcriber,
                microphone = { MicrophoneSource() },
                models = SelectedModelSource(modelStore()) {
                    controller.settings.value.dictationModelId
                },
                permission = MicrophonePermission.of(this),
                config = { controller.settings.value.toDictationConfig() },
                onResult = { controller.onDictationResult(it) }
            ),
            actions = AndroidDictationActions(this),
            permissionResults = MicrophonePermissionFlow.results,
            scope = scope
        )
        controller = KeyboardController(
            logic = InputLogic(),
            repository = settingsRepository(),
            scope = scope,
            feedback = Feedback(this),
            engine = engine,
            styles = styleRepository(),
            dictation = dictation,
            clipStore = clipStore(this),
            emojiLoader = { loadEmojiData(this@UltimateKeysService, listOf("en", "es")) },
            showImePicker = {
                getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            },
            openSettings = { openAppSettings() },
            openLanguages = { openAppSettings(MainRoutes.LANGUAGES) },
            logLatency = if (debuggable) { msg -> Log.d("UKLatency", msg) } else null
        )
        clipboardWatcher =
            SystemClipboardWatcher(this, controller.clipboard, scope).also { it.start() }
        startLanguages()
    }

    /** Opens the app (the settings, or [route] inside them) in its own task, above the keyboard. */
    private fun openAppSettings(route: String? = null) {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        route?.let { intent.putExtra(MainRoutes.EXTRA_ROUTE, it) }
        startActivity(intent)
    }

    /**
     * Follows the enabled and active languages: builds the dictionaries of the enabled ones in the background
     * (seconds, first time only), then switches suggestions and gesture typing to them.
     */
    private fun startLanguages() {
        if (!ENGINE_ENABLED) return
        val host = AndroidLanguageHost(
            context = this,
            engine = engine,
            gesture = controller.gesture,
            layoutFor = { tag -> controller.settings.value.layoutOf(tag) },
            log = { Log.w("UltimateKeys", it) }
        )
        languageHost = host
        LanguageEngineManager(
            scope = scope,
            dispatcher = Dispatchers.IO.limitedParallelism(1),
            host = host,
            mutableStatus = SharedLanguageStatus.flow
        ).also {
            languages = it
            it.start(settingsRepository().settings)
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
            firstShow?.let { timer ->
                viewTreeObserver.addOnDrawListener(
                    object : ViewTreeObserver.OnDrawListener {
                        override fun onDraw() {
                            timer.markDrawn()
                            post { viewTreeObserver.removeOnDrawListener(this) }
                        }
                    }
                )
            }
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
        controller.privacy.onStartInput(info.inputType, info.imeOptions)
        val ic = currentInputConnection ?: return
        controller.logic.onStartInput(
            AndroidEditorConnection(ic),
            EditorContext.from(info.inputType, info.imeOptions),
            restarting,
            info.initialSelStart
        )
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        controller.onKeyboardHidden()
        controller.panels.close()
        controller.logic.onFinishInput()
        controller.privacy.onKeyboardClosed()
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

    /** Short of memory: the speech model is the biggest thing we hold, let it go while idle. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) controller.dictation?.trimMemory()
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        controller.onKeyboardHidden()
        clipboardWatcher?.stop()
        scope.cancel()
        transcriber.close()
        languages?.stop()
        engine.close()
        languageHost?.close()
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
