// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.styles

import androidx.annotation.StringRes
import com.qtekfun.ultimatekeys.R

/** The screens of the style editor: its overview and one screen per group of controls. */
enum class EditorSection(@param:StringRes val title: Int) {
    Overview(R.string.style_editor_title),
    Appearance(R.string.style_appearance),
    Keys(R.string.group_keys),
    Labels(R.string.group_labels),
    Background(R.string.group_background),
    Feedback(R.string.group_feedback),
    SuggestionBar(R.string.group_suggestions),
    BottomRow(R.string.group_bottom_row),
    Panels(R.string.group_panels),
    Motion(R.string.group_motion),
    ColorsLight(R.string.group_colors_light),
    ColorsDark(R.string.group_colors_dark)
}
