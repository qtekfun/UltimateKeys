// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import com.qtekfun.ultimatekeys.ime.KeyboardController
import com.qtekfun.ultimatekeys.layouts.ActionKey
import com.qtekfun.ultimatekeys.layouts.CharKey
import com.qtekfun.ultimatekeys.layouts.KeyAction
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pixel sizes the gestures depend on. */
data class GestureMetrics(
    val slop: Float,
    val dragStep: Float,
    val chooserMinCell: Float,
    val chooserHeight: Float
)

/**
 * Interprets touches: multi-touch key presses, key slide correction, long-press alternatives with
 * slide-to-select, delete repeat and selection drag, spacebar cursor control. Main thread only.
 */
class SurfaceGestures(
    private val controller: KeyboardController,
    private val scope: CoroutineScope,
    private val publish: (List<PressView>) -> Unit
) {
    var geometry: KeyGeometry? = null
    var metrics = GestureMetrics(slop = 1f, dragStep = 1f, chooserMinCell = 1f, chooserHeight = 1f)

    private class Press(var key: PlacedKey, val downX: Float) {
        var lastX = downX
        var mode = PressMode.NORMAL
        var chooser: ChooserView? = null
        var longPressHandled = false
        var job: Job? = null
        var dragSteps = DragSteps(1f)
        var anchor = -1
        var selectionStepCount = 0

        fun cancelJob() {
            job?.cancel()
            job = null
        }
    }

    private val presses = LinkedHashMap<Long, Press>()

    fun down(id: Long, x: Float, y: Float) {
        val geo = geometry ?: return
        val placed = geo.keyAt(x, y) ?: return
        val press = Press(placed, x)
        press.dragSteps = DragSteps(metrics.dragStep)
        presses[id] = press
        val action = (placed.key as? ActionKey)?.action
        controller.keyDown(action)
        when (val key = placed.key) {
            is CharKey -> scheduleLongPress(press) {
                if (key.alternatives.isNotEmpty()) openChooser(press, key)
            }

            is ActionKey -> startActionKey(press, key.action)
        }
        publishState()
    }

    private fun startActionKey(press: Press, action: KeyAction) {
        when (action) {
            KeyAction.DELETE -> {
                controller.onAction(KeyAction.DELETE)
                press.job = scope.launch { repeatDelete() }
            }

            KeyAction.SPACE -> scheduleLongPress(press) { press.enterMode(PressMode.CURSOR) }

            KeyAction.GLOBE -> scheduleLongPress(press) {
                press.longPressHandled = true
                controller.onGlobeLongPress()
            }

            else -> Unit
        }
    }

    private suspend fun repeatDelete() {
        delay(KeyRepeat.INITIAL_DELAY_MS)
        var index = 0
        while (true) {
            repeat(KeyRepeat.charsPerTick(index)) { controller.onAction(KeyAction.DELETE) }
            delay(KeyRepeat.intervalMs(index))
            index++
        }
    }

    private fun scheduleLongPress(press: Press, action: () -> Unit) {
        press.cancelJob()
        press.job = scope.launch {
            delay(controller.settings.value.longPressDelayMs.toLong())
            action()
            publishState()
        }
    }

    private fun Press.enterMode(newMode: PressMode) {
        cancelJob()
        mode = newMode
        dragSteps = DragSteps(metrics.dragStep)
        anchor = controller.logic.cursor
        selectionStepCount = 0
    }

    private fun openChooser(press: Press, key: CharKey) {
        val geo = geometry ?: return
        val items = key.alternatives
        val cell = min(max(press.key.width, metrics.chooserMinCell), geo.width / items.size)
        val total = cell * items.size
        val left = (press.key.centerX - total / 2f).coerceIn(0f, max(0f, geo.width - total))
        val top = max(0f, press.key.top - metrics.chooserHeight - 2f)
        press.chooser = ChooserView(items, left, top, cell, metrics.chooserHeight, selected = -1)
        press.longPressHandled = true
    }

    fun move(id: Long, x: Float, y: Float) {
        val press = presses[id] ?: return
        val chooser = press.chooser
        when {
            chooser != null -> press.chooser = chooser.copy(selected = chooser.indexAt(x))

            press.mode == PressMode.CURSOR -> controller.logic.moveCursor(
                press.dragSteps.add(x - press.lastX)
            )

            press.mode == PressMode.DELETE_SELECT -> dragSelection(
                press,
                press.dragSteps.add(x - press.lastX)
            )

            else -> moveNormal(press, x, y)
        }
        press.lastX = x
        publishState()
    }

    private fun moveNormal(press: Press, x: Float, y: Float) {
        val key = press.key.key
        val travelled = x - press.downX
        when {
            key is ActionKey && key.action == KeyAction.SPACE && abs(travelled) > metrics.slop ->
                press.enterMode(PressMode.CURSOR)

            key is ActionKey && key.action == KeyAction.DELETE && travelled < -metrics.slop ->
                press.enterMode(PressMode.DELETE_SELECT)

            key is CharKey -> slideToNeighbour(press, x, y)
        }
    }

    /** Lets a finger that landed slightly wrong slide onto the intended character key. */
    private fun slideToNeighbour(press: Press, x: Float, y: Float) {
        val next = geometry?.keyAt(x, y) ?: return
        val nextKey = next.key
        if (next == press.key || nextKey !is CharKey) return
        press.key = next
        press.longPressHandled = false
        scheduleLongPress(press) {
            if (nextKey.alternatives.isNotEmpty()) openChooser(press, nextKey)
        }
    }

    private fun dragSelection(press: Press, steps: Int) {
        val logic = controller.logic
        if (steps < 0) {
            repeat(-steps) {
                press.selectionStepCount++
                val chars = if (press.selectionStepCount <=
                    CHAR_STEPS
                ) {
                    1
                } else {
                    max(1, logic.wordLengthBeforeCursor())
                }
                logic.extendSelectionLeft(chars, press.anchor)
            }
        } else if (steps > 0) {
            repeat(steps) {
                press.selectionStepCount = max(0, press.selectionStepCount - 1)
                logic.shrinkSelection(1, press.anchor)
            }
        }
    }

    fun up(id: Long) {
        val press = presses.remove(id) ?: return
        press.cancelJob()
        val started = System.nanoTime()
        val committed = release(press)
        if (committed) controller.latency.record(System.nanoTime() - started)
        publishState()
    }

    fun cancel(id: Long) {
        presses.remove(id)?.cancelJob()
        publishState()
    }

    fun cancelAll() {
        presses.values.forEach { it.cancelJob() }
        presses.clear()
        publishState()
    }

    /** Applies the key on release. Returns true when text was committed to the editor. */
    private fun release(press: Press): Boolean {
        val chooser = press.chooser
        val key = press.key.key
        return when {
            chooser != null -> {
                val choice = chooser.items.getOrNull(chooser.selected)
                controller.onText(choice ?: (key as CharKey).output)
                true
            }

            press.mode == PressMode.CURSOR -> false

            press.mode == PressMode.DELETE_SELECT -> {
                controller.logic.deleteSelection()
                true
            }

            press.longPressHandled && key is ActionKey -> false

            key is CharKey -> {
                controller.onText(key.output)
                true
            }

            key is ActionKey && key.action != KeyAction.DELETE -> {
                controller.onAction(key.action)
                key.action == KeyAction.SPACE || key.action == KeyAction.ENTER
            }

            else -> false
        }
    }

    private fun publishState() {
        publish(presses.values.map { PressView(it.key, it.mode, it.chooser) })
    }

    private companion object {
        const val CHAR_STEPS = 5
    }
}
