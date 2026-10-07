// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.logic

import android.text.InputType
import android.view.inputmethod.EditorInfo

enum class InputKind { TEXT, EMAIL, URL, PASSWORD, NUMBER, PHONE, NONE }

enum class EnterKind { ENTER, GO, SEARCH, SEND, NEXT, DONE, PREVIOUS }

/** What the logic needs to know about the focused field, derived from EditorInfo. */
data class EditorContext(
    val kind: InputKind = InputKind.TEXT,
    val enterKind: EnterKind = EnterKind.ENTER,
    val actionId: Int = EditorInfo.IME_ACTION_NONE,
    val multiLine: Boolean = false,
    val noEnterAction: Boolean = false,
    val inputType: Int = 0
) {
    /** True when Enter should run the editor action instead of inserting a line break. */
    val enterRunsAction: Boolean get() = enterKind != EnterKind.ENTER && !noEnterAction

    /** Text fields where composing text, suggestions and auto-capitalization make sense. */
    val isTextual: Boolean get() = kind == InputKind.TEXT

    companion object {
        fun from(inputType: Int, imeOptions: Int): EditorContext {
            val action = imeOptions and EditorInfo.IME_MASK_ACTION
            return EditorContext(
                kind = kindOf(inputType),
                enterKind = enterKindOf(action),
                actionId = action,
                multiLine = inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0,
                noEnterAction = imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0,
                inputType = inputType
            )
        }

        private fun enterKindOf(action: Int) = when (action) {
            EditorInfo.IME_ACTION_GO -> EnterKind.GO
            EditorInfo.IME_ACTION_SEARCH -> EnterKind.SEARCH
            EditorInfo.IME_ACTION_SEND -> EnterKind.SEND
            EditorInfo.IME_ACTION_NEXT -> EnterKind.NEXT
            EditorInfo.IME_ACTION_DONE -> EnterKind.DONE
            EditorInfo.IME_ACTION_PREVIOUS -> EnterKind.PREVIOUS
            else -> EnterKind.ENTER
        }

        private fun kindOf(inputType: Int): InputKind {
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            return when (inputType and InputType.TYPE_MASK_CLASS) {
                InputType.TYPE_CLASS_TEXT -> textKind(variation)

                InputType.TYPE_CLASS_NUMBER ->
                    if (variation ==
                        InputType.TYPE_NUMBER_VARIATION_PASSWORD
                    ) {
                        InputKind.PASSWORD
                    } else {
                        InputKind.NUMBER
                    }

                InputType.TYPE_CLASS_DATETIME -> InputKind.NUMBER

                InputType.TYPE_CLASS_PHONE -> InputKind.PHONE

                else -> InputKind.NONE
            }
        }

        private fun textKind(variation: Int) = when (variation) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            -> InputKind.PASSWORD

            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
            -> InputKind.EMAIL

            InputType.TYPE_TEXT_VARIATION_URI -> InputKind.URL

            else -> InputKind.TEXT
        }
    }
}
