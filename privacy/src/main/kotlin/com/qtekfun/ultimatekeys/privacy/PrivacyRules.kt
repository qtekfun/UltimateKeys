// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.privacy

/** Why private mode is on. */
enum class PrivacyReason {
    /** The app asked the keyboard not to learn (`IME_FLAG_NO_PERSONALIZED_LEARNING`), e.g. incognito tabs. */
    NO_PERSONALIZED_LEARNING,

    /** A password-like field. */
    PASSWORD,

    /** The person turned it on. */
    MANUAL
}

/** The outcome of the rules: private or not, and the first reason that applies. */
data class PrivacyDecision(val isPrivate: Boolean, val reason: PrivacyReason? = null) {
    companion object {
        val Off = PrivacyDecision(false)
    }
}

/** How long a manual switch-on lasts. */
enum class ManualDuration {
    UNTIL_TURNED_OFF,
    UNTIL_KEYBOARD_CLOSES
}

/**
 * The rules of private mode (SPEC section 9). Pure and free of Android types: the editor's
 * `inputType` and `imeOptions` are passed as the integers Android gives us, and the constants are
 * the platform's fixed values (`InputType`, `EditorInfo`), checked against the platform in the
 * app's instrumented tests.
 */
object PrivacyRules {
    const val IME_FLAG_NO_PERSONALIZED_LEARNING = 0x1000000

    private const val CLASS_MASK = 0x0000000f
    private const val VARIATION_MASK = 0x00000ff0
    private const val CLASS_TEXT = 0x1
    private const val CLASS_NUMBER = 0x2
    private const val TEXT_VARIATION_PASSWORD = 0x80
    private const val TEXT_VARIATION_VISIBLE_PASSWORD = 0x90
    private const val TEXT_VARIATION_WEB_PASSWORD = 0xe0
    private const val NUMBER_VARIATION_PASSWORD = 0x10

    /** Automatic activation: an app opt-out, or a password-like input type. */
    fun automaticReason(inputType: Int, imeOptions: Int): PrivacyReason? = when {
        imeOptions and IME_FLAG_NO_PERSONALIZED_LEARNING != 0 ->
            PrivacyReason.NO_PERSONALIZED_LEARNING

        isPassword(inputType) -> PrivacyReason.PASSWORD

        else -> null
    }

    fun isPassword(inputType: Int): Boolean {
        val variation = inputType and VARIATION_MASK
        return when (inputType and CLASS_MASK) {
            CLASS_TEXT -> variation in TEXT_PASSWORD_VARIATIONS
            CLASS_NUMBER -> variation == NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    /** Automatic reasons win over the manual switch, so the indicator says why it cannot be turned off. */
    fun shouldBePrivate(inputType: Int, imeOptions: Int, manualToggle: Boolean): PrivacyDecision {
        val automatic = automaticReason(inputType, imeOptions)
        return when {
            automatic != null -> PrivacyDecision(true, automatic)
            manualToggle -> PrivacyDecision(true, PrivacyReason.MANUAL)
            else -> PrivacyDecision.Off
        }
    }

    private val TEXT_PASSWORD_VARIATIONS = setOf(
        TEXT_VARIATION_PASSWORD,
        TEXT_VARIATION_VISIBLE_PASSWORD,
        TEXT_VARIATION_WEB_PASSWORD
    )
}
