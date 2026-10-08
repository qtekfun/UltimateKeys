// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.settings

import androidx.annotation.StringRes
import com.qtekfun.ultimatekeys.ImeStatus
import com.qtekfun.ultimatekeys.R
import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.ui.BadgeAccent
import com.qtekfun.ultimatekeys.ui.UkGlyph

/** The screens that live inside the main activity, each with its title. */
enum class SettingsRoute(@StringRes val title: Int) {
    Home(R.string.app_name),
    Setup(R.string.section_setup),
    Typing(R.string.section_typing),
    Languages(R.string.section_languages),
    Suggestions(R.string.section_suggestions),
    Gestures(R.string.section_gestures),
    Feedback(R.string.section_feedback),
    Clipboard(R.string.section_clipboard),
    Privacy(R.string.section_privacy),
    About(R.string.section_about)
}

/**
 * What a row of the home list opens. Most open a [route] inside the main activity; Appearance and
 * Dictation have their own activities (the style gallery and the dictation settings and models).
 */
enum class HomeTarget(@param:StringRes val title: Int, val route: SettingsRoute?) {
    Setup(R.string.section_setup, SettingsRoute.Setup),
    Typing(R.string.section_typing, SettingsRoute.Typing),
    Suggestions(R.string.section_suggestions, SettingsRoute.Suggestions),
    Gestures(R.string.section_gestures, SettingsRoute.Gestures),
    Feedback(R.string.section_feedback, SettingsRoute.Feedback),
    Appearance(R.string.section_appearance, null),
    Dictation(R.string.section_dictation, null),
    Clipboard(R.string.section_clipboard, SettingsRoute.Clipboard),
    Privacy(R.string.section_privacy, SettingsRoute.Privacy),
    About(R.string.section_about, SettingsRoute.About)
}

/** The short state shown at the end of a home row. */
enum class Summary(@param:StringRes val text: Int) {
    Ready(R.string.summary_ready),
    NeedsSetup(R.string.summary_needs_setup),
    On(R.string.summary_on),
    Off(R.string.summary_off)
}

/** One row of the home list: where it goes, its icon and its optional summary. */
data class HomeEntry(
    val target: HomeTarget,
    val glyph: UkGlyph,
    val accent: BadgeAccent,
    val summary: Summary? = null
)

/** Builds the home list from the current settings and the keyboard's system status. */
object HomeModel {
    fun groups(settings: KeyboardSettings, status: ImeStatus): List<List<HomeEntry>> = listOf(
        listOf(
            HomeEntry(
                HomeTarget.Setup,
                UkGlyph.Setup,
                BadgeAccent.Tertiary,
                if (status.enabled && status.selected) Summary.Ready else Summary.NeedsSetup
            )
        ),
        listOf(
            HomeEntry(HomeTarget.Typing, UkGlyph.Keyboard, BadgeAccent.Primary),
            HomeEntry(
                HomeTarget.Suggestions,
                UkGlyph.Suggestions,
                BadgeAccent.Secondary,
                onOff(settings.showSuggestions)
            ),
            HomeEntry(
                HomeTarget.Gestures,
                UkGlyph.Gestures,
                BadgeAccent.Tertiary,
                onOff(settings.gestureTyping)
            ),
            HomeEntry(HomeTarget.Feedback, UkGlyph.Feedback, BadgeAccent.Primary)
        ),
        listOf(
            HomeEntry(HomeTarget.Appearance, UkGlyph.Palette, BadgeAccent.Secondary),
            HomeEntry(
                HomeTarget.Dictation,
                UkGlyph.Mic,
                BadgeAccent.Primary,
                onOff(settings.dictationEnabled)
            ),
            HomeEntry(HomeTarget.Clipboard, UkGlyph.Clipboard, BadgeAccent.Tertiary),
            HomeEntry(HomeTarget.Privacy, UkGlyph.Lock, BadgeAccent.Secondary)
        ),
        listOf(HomeEntry(HomeTarget.About, UkGlyph.Info, BadgeAccent.Primary))
    )

    private fun onOff(on: Boolean) = if (on) Summary.On else Summary.Off
}
