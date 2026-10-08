// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.panels

import com.qtekfun.ultimatekeys.clipboard.ClipItem
import com.qtekfun.ultimatekeys.clipboard.ClipboardHistory
import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.core.SettingsRepository
import com.qtekfun.ultimatekeys.emoji.EmojiCategory
import com.qtekfun.ultimatekeys.emoji.EmojiData
import com.qtekfun.ultimatekeys.emoji.EmojiEntry
import com.qtekfun.ultimatekeys.emoji.EmojiHit
import com.qtekfun.ultimatekeys.emoji.EmojiRecents
import com.qtekfun.ultimatekeys.emoji.SkinTone
import com.qtekfun.ultimatekeys.layouts.CharKey
import com.qtekfun.ultimatekeys.layouts.KeyboardLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What replaces the keys, if anything. */
enum class PanelKind { NONE, EMOJI, CLIPBOARD }

/**
 * The state and actions of the emoji and clipboard panels. The Compose panels only draw this and
 * call back; everything that decides something lives here so it can be tested without a screen.
 * Main thread only.
 */
@Suppress("TooManyFunctions")
class PanelsController(
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    private val currentSettings: () -> KeyboardSettings,
    private val history: ClipboardHistory,
    private val loadEmoji: suspend () -> EmojiData,
    private val isPrivate: () -> Boolean,
    private val insert: (String) -> Unit
) {
    private val mutableKind = MutableStateFlow(PanelKind.NONE)
    val kind: StateFlow<PanelKind> = mutableKind.asStateFlow()

    private val mutableEmoji = MutableStateFlow<EmojiData?>(null)

    /** Null until the emoji data has loaded (the first time the panel opens). */
    val emoji: StateFlow<EmojiData?> = mutableEmoji.asStateFlow()

    /** The emoji tab on show; null is the "recent" tab. */
    private val mutableCategory = MutableStateFlow<EmojiCategory?>(null)
    val category: StateFlow<EmojiCategory?> = mutableCategory.asStateFlow()

    private val mutableSearching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = mutableSearching.asStateFlow()

    private val mutableQuery = MutableStateFlow("")
    val query: StateFlow<String> = mutableQuery.asStateFlow()

    private val mutableResults = MutableStateFlow<List<EmojiHit>>(emptyList())
    val results: StateFlow<List<EmojiHit>> = mutableResults.asStateFlow()

    val clips: StateFlow<List<ClipItem>> =
        history.items.stateIn(scope, SharingStarted.Lazily, emptyList())

    private var loading = false

    fun open(panel: PanelKind) {
        mutableKind.value = panel
        when (panel) {
            PanelKind.EMOJI -> {
                loadEmojiOnce()
                if (mutableCategory.value == null && recents().isEmpty()) {
                    mutableCategory.value = EmojiCategory.SMILEYS
                }
            }

            PanelKind.CLIPBOARD -> scope.launch { history.prune() }

            PanelKind.NONE -> resetSearch()
        }
    }

    fun close() = open(PanelKind.NONE)

    private fun loadEmojiOnce() {
        if (loading || mutableEmoji.value != null) return
        loading = true
        scope.launch {
            try {
                mutableEmoji.value = loadEmoji()
                if (mutableSearching.value) refreshSearch()
            } finally {
                loading = false
            }
        }
    }

    // --- Emoji ---

    fun recents(): List<String> = EmojiRecents.parse(currentSettings().emojiRecents)

    fun skinTone(): SkinTone = SkinTone.fromOrdinal(currentSettings().emojiSkinTone)

    fun selectCategory(category: EmojiCategory?) {
        mutableCategory.value = category
    }

    /** Inserts [entry] in the preferred skin tone; remembers it unless private mode is on. */
    fun pick(entry: EmojiEntry) = pickText(entry.forTone(skinTone()))

    /** Inserts an already chosen emoji (from the recents or from a search hit). */
    fun pickText(emoji: String) {
        insert(emoji)
        if (!isPrivate()) {
            scope.launch {
                settings.update { it.copy(emojiRecents = EmojiRecents.add(it.emojiRecents, emoji)) }
            }
        }
    }

    /** Next skin tone: none, then light to dark, then back to none. */
    fun cycleSkinTone() {
        val next = (skinTone().ordinal + 1) % SkinTone.entries.size
        scope.launch { settings.update { it.copy(emojiSkinTone = next) } }
    }

    fun openSearch() {
        loadEmojiOnce()
        mutableSearching.value = true
    }

    fun closeSearch() = resetSearch()

    fun typeQuery(text: String) = setQuery(mutableQuery.value + text)

    fun deleteQueryChar() = setQuery(mutableQuery.value.dropLast(1))

    private fun setQuery(value: String) {
        mutableQuery.value = value
        mutableResults.value = mutableEmoji.value?.search?.search(value).orEmpty()
    }

    /** Re-runs the search once the data has arrived after the first keystrokes. */
    fun refreshSearch() = setQuery(mutableQuery.value)

    private fun resetSearch() {
        mutableSearching.value = false
        mutableQuery.value = ""
        mutableResults.value = emptyList()
    }

    // --- Clipboard ---

    fun paste(item: ClipItem) = insert(item.text)

    fun togglePin(item: ClipItem) {
        scope.launch { history.setPinned(item.id, !item.pinned) }
    }

    fun delete(item: ClipItem) {
        scope.launch { history.delete(item.id) }
    }

    fun clearAll() {
        scope.launch { history.clearAll() }
    }
}

/** The letters the emoji search keyboard offers, taken from the active layout. */
object PanelLetters {
    private const val MIN_LETTERS = 5

    /** Letter rows only: no number row, no shift or backspace, nothing but character keys. */
    fun rows(layout: KeyboardLayout): List<List<CharKey>> = layout.rows
        .map { row -> row.keys.filterIsInstance<CharKey>() }
        .filter { keys ->
            keys.size >= MIN_LETTERS && keys.any { it.label.any(Char::isLetter) }
        }
}
