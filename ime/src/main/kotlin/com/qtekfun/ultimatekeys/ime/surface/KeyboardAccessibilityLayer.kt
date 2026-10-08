// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.IntOffset
import com.qtekfun.ultimatekeys.ime.KeyboardController
import com.qtekfun.ultimatekeys.ime.R
import com.qtekfun.ultimatekeys.ime.logic.EnterKind
import com.qtekfun.ultimatekeys.ime.logic.KeyboardState
import kotlin.math.roundToInt

/** True while any accessibility service runs; then, and only then, the keys get virtual nodes. */
@Composable
fun rememberAccessibilityActive(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    var active by remember(manager) { mutableStateOf(manager?.isEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.AccessibilityStateChangeListener { active = it }
        manager?.addAccessibilityStateChangeListener(listener)
        active = manager?.isEnabled == true
        onDispose { manager?.removeAccessibilityStateChangeListener(listener) }
    }
    return active
}

/**
 * Invisible, touch-transparent children laid over the key canvas, one per key and strip button.
 * They carry no pointer input, so typing touches pass through to the canvas; a screen reader
 * explores them and activates them with a click. [containerModifier] holds the keyboard-level
 * semantics (name, private-mode state).
 */
@Composable
fun KeyboardAccessibilityLayer(
    nodes: List<A11yNode>,
    onActivate: (A11yTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    Box(modifier.semantics { isTraversalGroup = true }) {
        nodes.forEachIndexed { index, node ->
            val width = with(density) { node.width.toDp() }
            val height = with(density) { node.height.toDp() }
            val actions = node.actions.map { action ->
                CustomAccessibilityAction(action.label) {
                    onActivate(action.target)
                    true
                }
            }
            Box(
                Modifier
                    .offset { IntOffset(node.left.roundToInt(), node.top.roundToInt()) }
                    .size(width, height)
                    .semantics {
                        contentDescription = node.description
                        role = Role.Button
                        traversalIndex = index.toFloat()
                        node.state?.let { stateDescription = it }
                        if (actions.isNotEmpty()) customActions = actions
                        onClick {
                            onActivate(node.target)
                            true
                        }
                    }
            )
        }
    }
}

/** The layer with its keyboard-level semantics, built from the controller and the surface state. */
@Composable
internal fun KeyboardAccessibilityOverlay(
    controller: KeyboardController,
    geometry: KeyGeometry,
    state: KeyboardState,
    strip: A11yStrip,
    modifier: Modifier = Modifier
) {
    val labels = rememberA11yLabels()
    val nodes = remember(geometry, state, strip, labels) {
        KeyAccessibility.nodes(geometry, state, strip, labels)
    }
    val privateState = stringResource(R.string.private_mode_on).takeIf { strip.privateOn }
    val toggle = stringResource(R.string.private_mode_toggle).takeIf { strip.canTogglePrivate }
    KeyboardAccessibilityLayer(
        nodes = nodes,
        onActivate = { AccessibilityTargets.activate(controller, it) },
        modifier = modifier.keyboardSemantics(
            hidden = false,
            description = stringResource(R.string.keyboard_description),
            privateState = privateState,
            toggle = toggle,
            onToggle = controller::togglePrivate
        )
    )
}

/**
 * The keyboard's own name, its private-mode state and the action that turns private mode on or off.
 * [hidden] removes all of it from the accessibility tree (the virtual nodes speak instead).
 */
internal fun Modifier.keyboardSemantics(
    hidden: Boolean,
    description: String,
    privateState: String?,
    toggle: String?,
    onToggle: () -> Unit
): Modifier = if (hidden) {
    clearAndSetSemantics { }
} else {
    semantics {
        contentDescription = description
        if (privateState != null) stateDescription = privateState
        if (toggle != null) {
            customActions = listOf(
                CustomAccessibilityAction(toggle) {
                    onToggle()
                    true
                }
            )
        }
    }
}

@Composable
internal fun rememberA11yLabels(): A11yLabels {
    val resources = LocalResources.current
    return remember(resources) {
        fun text(id: Int) = resources.getString(id)
        A11yLabels(
            enter = mapOf(
                EnterKind.ENTER to text(R.string.key_enter),
                EnterKind.GO to text(R.string.key_go),
                EnterKind.SEARCH to text(R.string.key_search),
                EnterKind.SEND to text(R.string.key_send),
                EnterKind.NEXT to text(R.string.key_next),
                EnterKind.DONE to text(R.string.key_done),
                EnterKind.PREVIOUS to text(R.string.key_previous)
            ),
            space = text(R.string.key_space),
            shift = text(R.string.key_shift),
            shiftOnce = text(R.string.key_shift_once),
            shiftLocked = text(R.string.key_shift_locked),
            shiftOff = text(R.string.key_shift_off),
            delete = text(R.string.key_delete),
            globe = text(R.string.key_language),
            keyboardPicker = text(R.string.key_choose_keyboard),
            symbols = text(R.string.key_symbols),
            letters = text(R.string.key_letters),
            moreSymbols = text(R.string.key_more_symbols),
            emoji = text(R.string.panel_emoji),
            dictate = text(R.string.key_dictate),
            clipboard = text(R.string.panel_clipboard),
            privateMode = text(R.string.private_mode_name),
            privateOn = text(R.string.private_mode_state_on),
            privateOff = text(R.string.private_mode_state_off),
            typeAlternative = { resources.getString(R.string.a11y_type_alternative, it) },
            suggestion = { resources.getString(R.string.a11y_suggestion, it) }
        )
    }
}
