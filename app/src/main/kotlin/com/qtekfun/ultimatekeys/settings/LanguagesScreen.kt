// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatekeys.R
import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.ime.language.LanguageStatus
import com.qtekfun.ultimatekeys.ime.language.SharedLanguageStatus
import com.qtekfun.ultimatekeys.languages.Language
import com.qtekfun.ultimatekeys.languages.LanguageCatalog
import com.qtekfun.ultimatekeys.ui.ScreenInsets
import com.qtekfun.ultimatekeys.ui.UkChoiceRow
import com.qtekfun.ultimatekeys.ui.UkOption
import com.qtekfun.ultimatekeys.ui.UkSwitchRow
import com.qtekfun.ultimatekeys.ui.group
import com.qtekfun.ultimatekeys.ui.section
import java.util.Locale

/**
 * Settings > Typing > Languages: which languages the keyboard writes and suggests in at once, in the order the
 * language key cycles through them, and which layout each uses.
 */
@Composable
internal fun LanguagesScreen(
    settings: KeyboardSettings,
    update: SettingsUpdate,
    onBack: () -> Unit,
    insets: ScreenInsets = ScreenInsets.current(),
    locale: Locale = Locale.getDefault()
) {
    val status by SharedLanguageStatus.flow.collectAsState()
    val rows = LanguagesModel.rows(settings)
    val preparing = stringResource(R.string.languages_status_preparing)
    val failed = stringResource(R.string.languages_status_failed)
    val footer = stringResource(R.string.languages_footer, LanguageCatalog.MAX_ENABLED)
    val rowTexts = rows.map { row ->
        val extra = when {
            row.replaces != null ->
                stringResource(R.string.languages_replaces, row.replaces.nativeName)

            row.enabled && status[row.language.tag] == LanguageStatus.PREPARING -> preparing

            row.enabled && status[row.language.tag] == LanguageStatus.FAILED -> failed

            else -> null
        }
        row to listOfNotNull(otherName(row.language, locale), extra).joinToString(" · ")
    }
    val choices = LanguagesModel.withLayoutChoice(settings)
    SubScreen(R.string.section_languages, onBack, insets) {
        group(header = null, footer = footer) {
            rowTexts.forEach { (entry, subtitle) ->
                row {
                    UkSwitchRow(
                        title = entry.language.nativeName,
                        checked = entry.enabled,
                        onChange = { on ->
                            update { LanguagesModel.toggle(it, entry.language.tag, on) }
                        },
                        subtitle = subtitle.ifEmpty { null },
                        enabled = entry.canToggle
                    )
                }
            }
        }
        if (choices.isNotEmpty()) {
            section(header = R.string.group_layout) {
                choices.forEach { language ->
                    row {
                        UkChoiceRow(
                            title = language.nativeName,
                            options = language.layoutIds.map {
                                UkOption(it, LanguagesModel.layoutName(it))
                            },
                            selected = settings.layoutOf(language.tag),
                            onSelect = { id ->
                                update { LanguagesModel.chooseLayout(it, language.tag, id) }
                            }
                        )
                    }
                }
            }
        }
        section(footer = R.string.languages_space_note) {
            toggle(R.string.setting_language_on_space, settings.showLanguageOnSpace) { v ->
                update { it.copy(showLanguageOnSpace = v) }
            }
        }
    }
}

/** The language's name in the language of the screen, when it differs from its own name. */
internal fun otherName(language: Language, shown: Locale): String? {
    val name = language.locale.getDisplayName(shown).replaceFirstChar { it.titlecase(shown) }
    return name.takeIf { !it.equals(language.nativeName, ignoreCase = true) }
}
