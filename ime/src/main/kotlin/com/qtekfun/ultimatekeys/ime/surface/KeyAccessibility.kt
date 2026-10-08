// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import com.qtekfun.ultimatekeys.ime.logic.EnterKind
import com.qtekfun.ultimatekeys.ime.logic.KeyboardState
import com.qtekfun.ultimatekeys.ime.logic.Page
import com.qtekfun.ultimatekeys.ime.logic.ShiftState
import com.qtekfun.ultimatekeys.ime.suggest.SuggestionState
import com.qtekfun.ultimatekeys.layouts.ActionKey
import com.qtekfun.ultimatekeys.layouts.CharKey
import com.qtekfun.ultimatekeys.layouts.KeyAction
import com.qtekfun.ultimatekeys.layouts.LayoutKey

/** What activating an accessibility node does. Resolved to a controller call by [AccessibilityTargets]. */
sealed interface A11yTarget {
    /** A character key (or one of its long-press alternatives): types [text]. */
    data class Type(val text: String) : A11yTarget

    data class Action(val action: KeyAction) : A11yTarget

    /** The input-method picker, normally a long press on the globe key. */
    data object KeyboardPicker : A11yTarget

    data object OpenSettings : A11yTarget

    /** The Languages screen of the app. */
    data object OpenLanguages : A11yTarget

    data object TogglePrivate : A11yTarget

    data object OpenClipboard : A11yTarget

    data class Suggestion(val slot: Int) : A11yTarget
}

/** An extra action of a node, announced by TalkBack in its actions menu. */
data class A11yAction(val label: String, val target: A11yTarget)

/**
 * One virtual accessibility child of the keyboard: a key or a button or a suggestion of the strip.
 * Bounds are in pixels of the surface. [state] is spoken after the description (on, off...).
 */
data class A11yNode(
    val id: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val description: String,
    val target: A11yTarget,
    val state: String? = null,
    val actions: List<A11yAction> = emptyList()
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** Spoken texts, resolved from resources by the composable so this file stays free of Android. */
data class A11yLabels(
    val enter: Map<EnterKind, String>,
    val space: String,
    val shift: String,
    val shiftOnce: String,
    val shiftLocked: String,
    val shiftOff: String,
    val delete: String,
    val globe: String,
    val keyboardPicker: String,
    val openSettings: String,
    val openLanguages: String,
    val symbols: String,
    val letters: String,
    val moreSymbols: String,
    val emoji: String,
    val dictate: String,
    val clipboard: String,
    val privateMode: String,
    val privateOn: String,
    val privateOff: String,
    val typeAlternative: (String) -> String,
    val suggestion: (String) -> String
)

/** What the suggestion strip shows and which of its buttons exist. */
data class A11yStrip(
    val suggestions: SuggestionState,
    val toggle: Boolean,
    val privateOn: Boolean,
    val canTogglePrivate: Boolean,
    val mic: Boolean,
    val tools: Boolean,
    val marginMic: MarginMic? = null
)

/**
 * Builds the accessibility tree of the keyboard from the same geometry the keys are drawn with.
 *
 * Nodes come in reading order: the suggestion strip first (left to right), then the key rows top to
 * bottom. The tree is only built and composed while a screen reader runs, so the normal touch path
 * pays nothing for it.
 */
object KeyAccessibility {
    fun nodes(
        geometry: KeyGeometry,
        state: KeyboardState,
        strip: A11yStrip,
        labels: A11yLabels,
        language: String? = null
    ): List<A11yNode> = stripNodes(geometry, strip, labels) +
        keyNodes(geometry, state, labels, language) +
        listOfNotNull(marginMicNode(strip, labels))

    private fun marginMicNode(strip: A11yStrip, labels: A11yLabels): A11yNode? =
        strip.marginMic?.let {
            A11yNode(
                id = "margin-mic",
                left = it.left,
                top = it.top,
                right = it.right,
                bottom = it.bottom,
                description = labels.dictate,
                target = A11yTarget.Action(KeyAction.MIC)
            )
        }

    fun keyNodes(
        geometry: KeyGeometry,
        state: KeyboardState,
        labels: A11yLabels,
        language: String? = null
    ): List<A11yNode> = geometry.keys.mapIndexed { index, placed ->
        val spoken = describe(placed.key, state, labels, language)
        A11yNode(
            id = "key-$index",
            left = placed.left,
            top = placed.top,
            right = placed.right,
            bottom = placed.bottom,
            description = spoken.description,
            target = spoken.target,
            state = spoken.state,
            actions = spoken.actions
        )
    }

    private class Spoken(
        val description: String,
        val target: A11yTarget,
        val state: String? = null,
        val actions: List<A11yAction> = emptyList()
    )

    private fun describe(
        key: LayoutKey,
        state: KeyboardState,
        labels: A11yLabels,
        language: String?
    ): Spoken = when (key) {
        is CharKey -> Spoken(
            description = letterCase(key.label, state),
            target = A11yTarget.Type(key.output),
            actions = key.alternatives.map {
                A11yAction(labels.typeAlternative(it), A11yTarget.Type(it))
            }
        )

        is ActionKey -> describeAction(key, state, labels, language)
    }

    /** Letters are spoken in the case they will be typed in. */
    private fun letterCase(label: String, state: KeyboardState): String =
        if (state.page == Page.LETTERS &&
            state.shift != ShiftState.OFF
        ) {
            label.uppercase()
        } else {
            label
        }

    private fun describeAction(
        key: ActionKey,
        state: KeyboardState,
        labels: A11yLabels,
        language: String?
    ): Spoken {
        val target = A11yTarget.Action(key.action)
        return when (key.action) {
            KeyAction.SHIFT -> Spoken(
                labels.shift,
                target,
                state = when (state.shift) {
                    ShiftState.OFF -> labels.shiftOff
                    ShiftState.ONCE -> labels.shiftOnce
                    ShiftState.LOCKED -> labels.shiftLocked
                }
            )

            KeyAction.DELETE -> Spoken(labels.delete, target)

            KeyAction.ENTER -> Spoken(labels.enter.getValue(state.enterKind), target)

            KeyAction.SPACE -> Spoken(labels.space, target)

            KeyAction.GLOBE -> Spoken(
                labels.globe,
                target,
                state = language,
                actions = listOf(
                    A11yAction(labels.keyboardPicker, A11yTarget.KeyboardPicker),
                    A11yAction(labels.openLanguages, A11yTarget.OpenLanguages),
                    A11yAction(labels.openSettings, A11yTarget.OpenSettings)
                )
            )

            KeyAction.EMOJI -> Spoken(labels.emoji, target)

            KeyAction.MIC -> Spoken(labels.dictate, target)

            KeyAction.SWITCH_LETTERS -> Spoken(labels.letters, target)

            KeyAction.SWITCH_SYMBOLS -> Spoken(labels.symbols, target)

            KeyAction.SWITCH_SYMBOLS_2 -> Spoken(labels.moreSymbols, target)
        }
    }

    /** The strip buttons and the non-empty suggestion slots; bounds mirror [StripLayout]. */
    fun stripNodes(geometry: KeyGeometry, strip: A11yStrip, labels: A11yLabels): List<A11yNode> {
        if (geometry.top <= 0f) return emptyList()
        val layout = StripLayout(
            geometry.width,
            geometry.top,
            strip.toggle,
            strip.mic,
            if (strip.tools) TOOL_BUTTONS else 0
        )
        val height = geometry.top
        val nodes = ArrayList<A11yNode>()
        if (strip.toggle) nodes += toggleNode(layout, height, strip, labels)
        if (strip.tools) nodes += toolNodes(layout, height, labels)
        strip.suggestions.slots.forEachIndexed { slot, word ->
            if (word.isNotEmpty()) {
                val left = layout.toolsWidth + slot * layout.cellWidth
                nodes += node(
                    "strip-suggestion-$slot",
                    left..left + layout.cellWidth,
                    height,
                    labels.suggestion(word),
                    A11yTarget.Suggestion(slot)
                )
            }
        }
        if (strip.mic) {
            nodes += node(
                "strip-mic",
                geometry.width - layout.micWidth..geometry.width,
                height,
                labels.dictate,
                A11yTarget.Action(KeyAction.MIC)
            )
        }
        return nodes.sortedBy { it.left }
    }

    private fun toggleNode(
        layout: StripLayout,
        height: Float,
        strip: A11yStrip,
        labels: A11yLabels
    ) = node(
        "strip-private",
        0f..layout.toggleWidth,
        height,
        labels.privateMode,
        A11yTarget.TogglePrivate
    ).copy(state = if (strip.privateOn) labels.privateOn else labels.privateOff)

    private fun toolNodes(layout: StripLayout, height: Float, labels: A11yLabels) = listOf(
        node(
            "strip-clipboard",
            layout.toggleWidth..layout.toggleWidth + height,
            height,
            labels.clipboard,
            A11yTarget.OpenClipboard
        ),
        node(
            "strip-emoji",
            layout.toggleWidth + height..layout.toggleWidth + 2 * height,
            height,
            labels.emoji,
            A11yTarget.Action(KeyAction.EMOJI)
        )
    )

    /** A strip node spanning [horizontal] and as tall as the strip. */
    private fun node(
        id: String,
        horizontal: ClosedFloatingPointRange<Float>,
        height: Float,
        description: String,
        target: A11yTarget
    ) = A11yNode(
        id,
        horizontal.start,
        0f,
        horizontal.endInclusive,
        height,
        description,
        target
    )

    private const val TOOL_BUTTONS = 2
}
