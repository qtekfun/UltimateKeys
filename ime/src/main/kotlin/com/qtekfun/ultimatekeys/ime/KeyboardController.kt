// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime

import com.qtekfun.ultimatekeys.clipboard.ClipStore
import com.qtekfun.ultimatekeys.clipboard.ClipboardHistory
import com.qtekfun.ultimatekeys.clipboard.ClipboardPolicy
import com.qtekfun.ultimatekeys.clipboard.MemoryClipStore
import com.qtekfun.ultimatekeys.clipboard.Retention
import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.core.SettingsRepository
import com.qtekfun.ultimatekeys.emoji.EmojiCatalog
import com.qtekfun.ultimatekeys.emoji.EmojiData
import com.qtekfun.ultimatekeys.emoji.EmojiSearch
import com.qtekfun.ultimatekeys.engine.NoopSuggestionEngine
import com.qtekfun.ultimatekeys.engine.SuggestionEngine
import com.qtekfun.ultimatekeys.ime.logic.InputLogic
import com.qtekfun.ultimatekeys.ime.logic.InputOptions
import com.qtekfun.ultimatekeys.ime.logic.Page
import com.qtekfun.ultimatekeys.ime.panels.PanelKind
import com.qtekfun.ultimatekeys.ime.panels.PanelsController
import com.qtekfun.ultimatekeys.ime.suggest.PrivacyGuardedEngine
import com.qtekfun.ultimatekeys.ime.suggest.SuggestionController
import com.qtekfun.ultimatekeys.ime.surface.LatencyTracker
import com.qtekfun.ultimatekeys.ime.voice.DictationHost
import com.qtekfun.ultimatekeys.layouts.BottomRow
import com.qtekfun.ultimatekeys.layouts.KeyAction
import com.qtekfun.ultimatekeys.layouts.KeyboardLayout
import com.qtekfun.ultimatekeys.layouts.LayoutRepository
import com.qtekfun.ultimatekeys.privacy.ManualDuration
import com.qtekfun.ultimatekeys.privacy.PrivacyState
import com.qtekfun.ultimatekeys.style.InMemoryStyleRepository
import com.qtekfun.ultimatekeys.style.Presets
import com.qtekfun.ultimatekeys.style.Style
import com.qtekfun.ultimatekeys.style.StyleRepository
import com.qtekfun.ultimatekeys.voice.DictationResult
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Connects the surface, the typing logic, the settings and the feedback. Main thread only. */
class KeyboardController(
    val logic: InputLogic,
    private val repository: SettingsRepository,
    private val scope: CoroutineScope,
    private val feedback: Feedback?,
    private val engine: SuggestionEngine = NoopSuggestionEngine,
    suggestionDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
    private val logLatency: ((String) -> Unit)? = null,
    styles: StyleRepository = InMemoryStyleRepository(),
    /** Dictation; null hides the microphone key (no speech engine in this build). */
    val dictation: DictationHost? = null,
    clipStore: ClipStore = MemoryClipStore(),
    emojiLoader: suspend () -> EmojiData = {
        EmojiData(EmojiCatalog(emptyList()), EmojiSearch(emptyList()))
    },
    private val showImePicker: () -> Unit
) {
    val settings: StateFlow<KeyboardSettings> =
        repository.settings.stateIn(scope, SharingStarted.Eagerly, KeyboardSettings())

    /** Whether typing is private right now; nothing is learned while it is. */
    val privacy = PrivacyState()

    /** The style the surface is drawn with; changes apply immediately. */
    val style: StateFlow<Style> =
        styles.active.stateIn(scope, SharingStarted.Eagerly, Presets.default)

    /** Optional keys that exist yet; the microphone appears once dictation is wired in. */
    val features = BottomRowFeatures(emoji = true, voice = dictation != null)

    /** The clipboard history; nothing reaches it while typing is private. */
    val clipboard = ClipboardHistory(
        store = clipStore,
        policy = {
            val s = settings.value
            ClipboardPolicy(
                enabled = s.clipboardEnabled,
                retention = Retention.fromId(s.clipboardRetention),
                maxItems = s.clipboardMaxItems
            )
        },
        isPrivate = { privacy.isPrivate }
    )

    /** The emoji and clipboard panels. */
    val panels = PanelsController(
        scope = scope,
        settings = repository,
        currentSettings = { settings.value },
        history = clipboard,
        loadEmoji = emojiLoader,
        isPrivate = { privacy.isPrivate },
        insert = { logic.insertVerbatim(it) }
    )
    val latency = LatencyTracker()
    val suggestions = SuggestionController(
        engine = PrivacyGuardedEngine(engine) { privacy.isPrivate },
        scope = scope,
        dispatcher = suggestionDispatcher,
        locale = { logic.locale },
        learningAllowed = { !privacy.isPrivate }
    )

    init {
        logic.suggestionHook = suggestions
        repository.settings.onEach(::apply).launchIn(scope)
    }

    private fun apply(s: KeyboardSettings) {
        privacy.manualDuration = if (s.privateModeEndsOnClose) {
            ManualDuration.UNTIL_KEYBOARD_CLOSES
        } else {
            ManualDuration.UNTIL_TURNED_OFF
        }
        logic.options = InputOptions(
            autoCapitalize = s.autoCapitalize,
            doubleSpacePeriod = s.doubleSpacePeriod,
            smartPunctuation = s.smartPunctuation,
            composeWords = s.showSuggestions,
            autoCorrect = s.autoCorrect && s.showSuggestions
        )
        logic.locale = Locale.forLanguageTag(LayoutRepository.load(s.letterLayoutId).locale ?: "en")
    }

    /** The keys of [page]; the letter and symbol pages follow the style's bottom row. */
    fun layoutFor(
        page: Page,
        settings: KeyboardSettings,
        style: Style = this.style.value
    ): KeyboardLayout {
        val slots = BottomRowPlan.slots(style.bottomRow, features)
        return when (page) {
            Page.LETTERS -> BottomRow.apply(
                LayoutRepository.pages(settings.letterLayoutId, settings.numberRow).letters,
                slots
            )

            Page.SYMBOLS_1 -> BottomRow.apply(LayoutRepository.load("symbols_1"), slots)

            Page.SYMBOLS_2 -> BottomRow.apply(LayoutRepository.load("symbols_2"), slots)

            Page.NUMERIC -> LayoutRepository.load("numeric")

            Page.PHONE -> LayoutRepository.load("phone")
        }
    }

    fun keyDown(action: KeyAction?) {
        val s = settings.value
        feedback?.keyDown(action, s.hapticIntensity, s.soundVolume)
    }

    fun recordLatency(nanos: Long) {
        latency.record(nanos)
        val log = logLatency ?: return
        if (latency.size() % LOG_EVERY == 0) {
            log(
                "key-to-commit p50=${latency.percentileMs(
                    P50
                )}ms p95=${latency.percentileMs(P95)}ms n=${latency.size()}"
            )
        }
    }

    fun onText(text: String) = logic.onText(text)

    fun onAction(action: KeyAction) {
        when (action) {
            KeyAction.SHIFT -> logic.onShiftTap()

            KeyAction.DELETE -> logic.onDelete()

            KeyAction.ENTER -> logic.onEnter()

            KeyAction.SPACE -> logic.onSpace()

            KeyAction.GLOBE -> cycleLayout()

            KeyAction.SWITCH_LETTERS -> logic.showPage(Page.LETTERS)

            KeyAction.SWITCH_SYMBOLS -> logic.showPage(Page.SYMBOLS_1)

            KeyAction.SWITCH_SYMBOLS_2 -> logic.showPage(Page.SYMBOLS_2)

            KeyAction.MIC -> {
                panels.close()
                dictation?.open()
            }

            KeyAction.EMOJI -> openPanel(PanelKind.EMOJI)
        }
    }

    /** Dictated text arrives: insert it as one edit; nothing is learned while private. */
    fun onDictationResult(result: DictationResult) {
        logic.insertDictation(result.text, result.language, learn = !privacy.isPrivate)
    }

    /** The keyboard is going away: a running dictation must not keep the microphone. */
    fun onKeyboardHidden() = dictation?.cancel()

    /** A tap on slot [index] of the suggestion strip. */
    fun onSuggestionTapped(index: Int) {
        val word = suggestions.state.value.slots.getOrNull(index).orEmpty()
        if (word.isNotEmpty()) logic.commitWithAutoSpace(word)
    }

    /** The clipboard button in the suggestion bar. */
    fun openClipboard() = openPanel(PanelKind.CLIPBOARD)

    /** Panels are exclusive: opening one ends a running dictation. */
    private fun openPanel(kind: PanelKind) {
        dictation?.cancel()
        panels.open(kind)
    }

    /** The private-mode button in the suggestion bar. */
    fun togglePrivate() = privacy.toggleManual()

    fun onGlobeLongPress() = showImePicker()

    private fun cycleLayout() {
        scope.launch {
            repository.update {
                val ids = LayoutRepository.letterLayoutIds
                val next = ids[(ids.indexOf(it.letterLayoutId) + 1).mod(ids.size)]
                it.copy(letterLayoutId = next)
            }
        }
    }

    private companion object {
        const val LOG_EVERY = 20
        const val P50 = 50.0
        const val P95 = 95.0
    }
}
