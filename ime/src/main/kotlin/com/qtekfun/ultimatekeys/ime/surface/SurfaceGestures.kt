// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import com.qtekfun.ultimatekeys.gesture.GestureCapture
import com.qtekfun.ultimatekeys.gesture.GestureKeyboard
import com.qtekfun.ultimatekeys.gesture.GestureSensitivity
import com.qtekfun.ultimatekeys.ime.KeyboardController
import com.qtekfun.ultimatekeys.ime.gesture.GestureSupport
import com.qtekfun.ultimatekeys.ime.logic.Page
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
@Suppress("TooManyFunctions")
class SurfaceGestures(
    private val controller: KeyboardController,
    private val scope: CoroutineScope,
    private val publish: (List<PressView>) -> Unit
) {
    var geometry: KeyGeometry? = null
        set(value) {
            if (field !== value) gestureKeyboard = null
            field = value
        }
    private var gestureKeyboard: GestureKeyboard? = null

    /** Whether the private-mode button sits at the left of the suggestion bar. */
    var stripToggleVisible = false
    var stripMicVisible = false

    /** The clipboard and emoji buttons are on the bar. */
    var stripToolsVisible = false
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

        /** Present while this touch may still turn into a gesture, or already has. */
        var capture: GestureCapture? = null

        fun cancelJob() {
            job?.cancel()
            job = null
        }
    }

    private val presses = LinkedHashMap<Long, Press>()
    private val stripPresses = HashMap<Long, Int>()

    fun down(id: Long, x: Float, y: Float) {
        val geo = geometry ?: return
        if (y < geo.top) {
            stripPresses[id] = slotAt(x)
            return
        }
        // A finger gliding over the letters owns the keyboard until it lifts: other fingers are ignored.
        if (presses.values.any { it.mode == PressMode.GESTURE }) return
        val placed = geo.keyAt(x, y) ?: return
        val press = Press(placed, x)
        press.dragSteps = DragSteps(metrics.dragStep)
        if (gestureAllowed(placed)) {
            val unit = placed.width * GestureSensitivity.startDistanceUnits(
                controller.settings.value.gestureSensitivity
            )
            press.capture = GestureCapture(unit).also { it.begin(x, y) }
        }
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

    /** A gesture can start only on a letter key of the letters page, with no other finger down. */
    private fun gestureAllowed(placed: PlacedKey): Boolean {
        val key = placed.key as? CharKey ?: return false
        return presses.isEmpty() && key.label.singleOrNull()?.isLetter() == true &&
            controller.settings.value.gestureTyping && controller.gesture.ready &&
            controller.logic.state.value.page == Page.LETTERS && !controller.panelOrDictationOpen
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
        press.capture = null
    }

    fun move(id: Long, x: Float, y: Float) {
        val press = presses[id] ?: return
        val chooser = press.chooser
        val capture = press.capture
        if (capture != null && chooser == null && capture.add(x, y)) startGesture(press)
        when {
            press.mode == PressMode.GESTURE -> Unit

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

    /** The finger has gone far enough: the touch is a gesture, not a key press. */
    private fun startGesture(press: Press) {
        press.cancelJob()
        press.chooser = null
        press.longPressHandled = true
        press.mode = PressMode.GESTURE
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

    private fun slotAt(x: Float): Int {
        val geo = geometry ?: return 0
        val strip = StripLayout(
            geo.width,
            geo.top,
            stripToggleVisible,
            stripMicVisible,
            if (stripToolsVisible) STRIP_TOOLS else 0
        )
        return when {
            strip.isToggle(x) -> TOGGLE_SLOT
            strip.isMic(x) -> MIC_SLOT
            strip.extraToolAt(x) == 0 -> CLIPBOARD_SLOT
            strip.extraToolAt(x) == 1 -> EMOJI_SLOT
            else -> strip.slotAt(x)
        }
    }

    fun up(id: Long, x: Float = -1f) {
        val slot = stripPresses.remove(id)
        if (slot != null) {
            if (x < 0f || slotAt(x) == slot) {
                when (slot) {
                    TOGGLE_SLOT -> controller.togglePrivate()
                    MIC_SLOT -> controller.onAction(KeyAction.MIC)
                    CLIPBOARD_SLOT -> controller.openClipboard()
                    EMOJI_SLOT -> controller.onAction(KeyAction.EMOJI)
                    else -> controller.onSuggestionTapped(slot)
                }
            }
            return
        }
        val press = presses.remove(id) ?: return
        press.cancelJob()
        if (press.mode == PressMode.GESTURE) {
            if (finishGesture(press)) {
                publishState()
                return
            }
            slideOntoKey(press)
        }
        val started = System.nanoTime()
        val committed = release(press)
        if (committed) controller.recordLatency(System.nanoTime() - started)
        publishState()
    }

    fun cancel(id: Long) {
        stripPresses.remove(id)
        presses.remove(id)?.cancelJob()
        publishState()
    }

    fun cancelAll() {
        presses.values.forEach { it.cancelJob() }
        presses.clear()
        stripPresses.clear()
        publishState()
    }

    /** Decodes the finished gesture. False when the path was too short to be a word: a key press. */
    private fun finishGesture(press: Press): Boolean {
        val geo = geometry ?: return false
        val capture = press.capture ?: return false
        if (capture.length < GestureSensitivity.MIN_PATH_UNITS * press.key.width) return false
        val keyboard = gestureKeyboard ?: GestureSupport.keyboard(geo).also { gestureKeyboard = it }
        controller.onGesture(capture.points(), keyboard)
        return true
    }

    /** A short slide that turned into a gesture too early is the key under the finger when it lifts. */
    private fun slideOntoKey(press: Press) {
        val last = press.capture?.tail(1)?.takeIf { it.size == 2 } ?: return
        geometry?.keyAt(last[0], last[1])?.let { press.key = it }
        press.mode = PressMode.NORMAL
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
        val trail = controller.settings.value.gestureTrail
        publish(
            presses.values.map {
                val capture = it.capture
                val points = if (trail && it.mode == PressMode.GESTURE && capture != null) {
                    capture.tail(TRAIL_POINTS)
                } else {
                    EMPTY_TRAIL
                }
                PressView(it.key, it.mode, it.chooser, points)
            }
        )
    }

    private companion object {
        const val CHAR_STEPS = 5
        const val TRAIL_POINTS = 48
        val EMPTY_TRAIL = FloatArray(0)
        const val TOGGLE_SLOT = -1
        const val MIC_SLOT = -2
        const val CLIPBOARD_SLOT = -3
        const val EMOJI_SLOT = -4
        const val STRIP_TOOLS = 2
    }
}
