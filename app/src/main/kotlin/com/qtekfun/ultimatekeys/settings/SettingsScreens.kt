// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("TooManyFunctions")

package com.qtekfun.ultimatekeys.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.qtekfun.ultimatekeys.AppVersion
import com.qtekfun.ultimatekeys.ImeStatus
import com.qtekfun.ultimatekeys.R
import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.languages.LanguageCatalog
import com.qtekfun.ultimatekeys.ui.ScreenInsets
import com.qtekfun.ultimatekeys.ui.UkChevron
import com.qtekfun.ultimatekeys.ui.UkGlyph
import com.qtekfun.ultimatekeys.ui.UkIcon
import com.qtekfun.ultimatekeys.ui.UkNavRow
import com.qtekfun.ultimatekeys.ui.UkOption
import com.qtekfun.ultimatekeys.ui.UkRow
import com.qtekfun.ultimatekeys.ui.UkScreen
import com.qtekfun.ultimatekeys.ui.UkSeparatorInset
import com.qtekfun.ultimatekeys.ui.UkSize
import com.qtekfun.ultimatekeys.ui.UkSpacing
import com.qtekfun.ultimatekeys.ui.UkTextField
import com.qtekfun.ultimatekeys.ui.UkTheme
import com.qtekfun.ultimatekeys.ui.UkValueRow
import com.qtekfun.ultimatekeys.ui.group
import com.qtekfun.ultimatekeys.ui.section

/** How a screen changes the settings: it hands over a transform of the current ones. */
internal typealias SettingsUpdate = ((KeyboardSettings) -> KeyboardSettings) -> Unit

@Composable
internal fun HomeScreen(
    settings: KeyboardSettings,
    status: ImeStatus,
    version: AppVersion,
    onOpen: (HomeTarget) -> Unit,
    modifier: Modifier = Modifier,
    insets: ScreenInsets = ScreenInsets.current()
) {
    val versionText = stringResource(R.string.app_version, version.name, version.code)
    UkScreen(title = stringResource(R.string.app_name), modifier = modifier, insets = insets) {
        HomeModel.groups(settings, status).forEachIndexed { index, entries ->
            group(key = "home-$index", separatorInset = UkSeparatorInset.WithBadge) {
                entries.forEach { entry ->
                    row {
                        UkNavRow(
                            title = stringResource(entry.target.title),
                            onClick = { onOpen(entry.target) },
                            value = entry.summary?.let { stringResource(it.text) },
                            glyph = entry.glyph,
                            accent = entry.accent
                        )
                    }
                }
            }
        }
        item(key = "version") {
            Text(
                versionText,
                modifier = Modifier.fillMaxWidth().padding(top = UkSpacing.lg),
                style = UkTheme.typography.footnote,
                color = UkTheme.colors.secondaryLabel,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** The frame every sub-screen shares: its title, a back button and the list. */
@Composable
internal fun SubScreen(
    title: Int,
    onBack: () -> Unit,
    insets: ScreenInsets,
    content: LazyListScope.() -> Unit
) {
    UkScreen(
        title = stringResource(title),
        insets = insets,
        onBack = onBack,
        backText = stringResource(R.string.nav_back),
        backDescription = stringResource(R.string.nav_back),
        content = content
    )
}

@Composable
internal fun SetupScreen(
    status: ImeStatus,
    onEnable: () -> Unit,
    onSelect: () -> Unit,
    onBack: () -> Unit,
    insets: ScreenInsets = ScreenInsets.current()
) {
    var sample by rememberSaveable { mutableStateOf("") }
    SubScreen(R.string.section_setup, onBack, insets) {
        section(header = R.string.setup_title) {
            row {
                StepRow(
                    stringResource(
                        if (status.enabled) R.string.step_enabled else R.string.step_enable
                    ),
                    done = status.enabled,
                    enabled = !status.enabled,
                    onClick = onEnable
                )
            }
            row {
                StepRow(
                    stringResource(
                        if (status.selected) R.string.step_selected else R.string.step_select
                    ),
                    done = status.selected,
                    enabled = status.enabled && !status.selected,
                    onClick = onSelect
                )
            }
        }
        section(header = R.string.try_it_here, footer = R.string.try_it_note) {
            row {
                UkTextField(
                    value = sample,
                    onValueChange = { sample = it },
                    label = stringResource(R.string.try_it_here)
                )
            }
        }
    }
}

@Composable
private fun StepRow(title: String, done: Boolean, enabled: Boolean, onClick: () -> Unit) {
    UkRow(
        title = title,
        onClick = onClick,
        enabled = enabled,
        trailing = {
            if (done) {
                UkIcon(UkGlyph.Check, UkTheme.colors.tint, size = UkSize.badgeGlyph)
            } else if (enabled) {
                UkChevron()
            }
        }
    )
}

@Composable
internal fun TypingScreen(
    settings: KeyboardSettings,
    update: SettingsUpdate,
    onOpenLanguages: () -> Unit,
    onBack: () -> Unit,
    insets: ScreenInsets = ScreenInsets.current()
) {
    val languages = settings.enabledLanguages.mapNotNull { LanguageCatalog.find(it) }
        .joinToString(", ") { it.nativeName }
    SubScreen(R.string.section_typing, onBack, insets) {
        section(header = R.string.group_size) {
            intSlider(
                R.string.setting_height,
                settings.heightPercent,
                KeyboardSettings.HEIGHT_RANGE,
                ValueFormat::percent
            ) { v -> update { it.copy(heightPercent = v) } }
            intSlider(
                R.string.setting_bottom_margin,
                settings.bottomMarginDp,
                KeyboardSettings.BOTTOM_MARGIN_RANGE,
                ValueFormat::dp
            ) { v -> update { it.copy(bottomMarginDp = v) } }
        }
        section(header = R.string.group_edges, footer = R.string.edges_note) {
            intSlider(
                R.string.setting_side_margin,
                settings.sideMarginDp,
                KeyboardSettings.SIDE_MARGIN_RANGE,
                ValueFormat::dp
            ) { v -> update { it.copy(sideMarginDp = v) } }
            intSlider(
                R.string.setting_edge_boost,
                settings.edgeKeyBoostPercent,
                KeyboardSettings.EDGE_BOOST_RANGE,
                ValueFormat::percent
            ) { v -> update { it.copy(edgeKeyBoostPercent = v) } }
        }
        section(header = R.string.group_layout) {
            row {
                UkNavRow(
                    stringResource(R.string.section_languages),
                    onClick = onOpenLanguages,
                    value = languages
                )
            }
            toggle(R.string.setting_number_row, settings.numberRow) { v ->
                update { it.copy(numberRow = v) }
            }
        }
        section(header = R.string.group_typing_help) {
            toggle(R.string.setting_auto_capitalize, settings.autoCapitalize) { v ->
                update { it.copy(autoCapitalize = v) }
            }
            toggle(R.string.setting_double_space, settings.doubleSpacePeriod) { v ->
                update { it.copy(doubleSpacePeriod = v) }
            }
            toggle(R.string.setting_smart_punctuation, settings.smartPunctuation) { v ->
                update { it.copy(smartPunctuation = v) }
            }
        }
        section(header = R.string.group_keys_press) {
            intSlider(
                R.string.setting_long_press,
                settings.longPressDelayMs,
                KeyboardSettings.LONG_PRESS_RANGE,
                ValueFormat::millis
            ) { v -> update { it.copy(longPressDelayMs = v) } }
        }
    }
}

@Composable
internal fun SuggestionsScreen(
    settings: KeyboardSettings,
    update: SettingsUpdate,
    onOpenDictionary: () -> Unit,
    onBack: () -> Unit,
    insets: ScreenInsets = ScreenInsets.current()
) {
    SubScreen(R.string.section_suggestions, onBack, insets) {
        group {
            toggle(R.string.setting_suggestions, settings.showSuggestions) { v ->
                update { it.copy(showSuggestions = v) }
            }
            toggle(R.string.setting_autocorrect, settings.autoCorrect) { v ->
                update { it.copy(autoCorrect = v) }
            }
        }
        section(footer = R.string.suggestions_learned_note) {
            row {
                UkNavRow(stringResource(R.string.user_dictionary_open), onClick = onOpenDictionary)
            }
        }
    }
}

@Composable
internal fun GesturesScreen(
    settings: KeyboardSettings,
    update: SettingsUpdate,
    onBack: () -> Unit,
    insets: ScreenInsets = ScreenInsets.current()
) {
    SubScreen(R.string.section_gestures, onBack, insets) {
        section(footer = R.string.gestures_note) {
            toggle(R.string.setting_gesture_typing, settings.gestureTyping) { v ->
                update { it.copy(gestureTyping = v) }
            }
        }
        if (settings.gestureTyping) {
            group {
                toggle(R.string.setting_gesture_trail, settings.gestureTrail) { v ->
                    update { it.copy(gestureTrail = v) }
                }
                intSlider(
                    R.string.setting_gesture_sensitivity,
                    settings.gestureSensitivity,
                    KeyboardSettings.PERCENT_RANGE,
                    ValueFormat::percent
                ) { v -> update { it.copy(gestureSensitivity = v) } }
            }
        }
    }
}

@Composable
internal fun FeedbackScreen(
    settings: KeyboardSettings,
    update: SettingsUpdate,
    onBack: () -> Unit,
    insets: ScreenInsets = ScreenInsets.current()
) {
    SubScreen(R.string.section_feedback, onBack, insets) {
        section(footer = R.string.feedback_note) {
            intSlider(
                R.string.setting_haptics,
                settings.hapticIntensity,
                KeyboardSettings.PERCENT_RANGE,
                ValueFormat::percent
            ) { v -> update { it.copy(hapticIntensity = v) } }
            intSlider(
                R.string.setting_sound,
                settings.soundVolume,
                KeyboardSettings.PERCENT_RANGE,
                ValueFormat::percent
            ) { v -> update { it.copy(soundVolume = v) } }
        }
    }
}

private val RETENTION_CHOICES = listOf(
    "hour" to R.string.retention_hour,
    "day" to R.string.retention_day,
    "week" to R.string.retention_week,
    "forever" to R.string.retention_forever
)

/** The clipboard history options (SPEC section 10). Pinned clips never expire. */
@Composable
internal fun ClipboardScreen(
    settings: KeyboardSettings,
    update: SettingsUpdate,
    onBack: () -> Unit,
    insets: ScreenInsets = ScreenInsets.current()
) {
    val retention = RETENTION_CHOICES.map { (id, label) -> UkOption(id, stringResource(label)) }
    SubScreen(R.string.section_clipboard, onBack, insets) {
        section(
            header = R.string.clipboard_title,
            footer = R.string.setting_clipboard_note
        ) {
            toggle(R.string.setting_clipboard_history, settings.clipboardEnabled) { v ->
                update { it.copy(clipboardEnabled = v) }
            }
            choice(
                R.string.setting_clipboard_retention,
                retention,
                settings.clipboardRetention
            ) { v ->
                update { it.copy(clipboardRetention = v) }
            }
            intSlider(
                R.string.setting_clipboard_max,
                settings.clipboardMaxItems,
                KeyboardSettings.CLIPBOARD_ITEMS_RANGE,
                Int::toString
            ) { v -> update { it.copy(clipboardMaxItems = v) } }
        }
    }
}

@Composable
internal fun PrivacyScreen(
    settings: KeyboardSettings,
    update: SettingsUpdate,
    onBack: () -> Unit,
    insets: ScreenInsets = ScreenInsets.current()
) {
    SubScreen(R.string.section_privacy, onBack, insets) {
        section(footer = R.string.privacy_note) {
            toggle(R.string.setting_private_ends_on_close, settings.privateModeEndsOnClose) { v ->
                update { it.copy(privateModeEndsOnClose = v) }
            }
        }
    }
}

@Composable
internal fun AboutScreen(
    version: AppVersion,
    onOpenLicenses: () -> Unit,
    onBack: () -> Unit,
    insets: ScreenInsets = ScreenInsets.current()
) {
    SubScreen(R.string.section_about, onBack, insets) {
        section(footer = R.string.about_note) {
            row {
                UkValueRow(
                    stringResource(R.string.about_version),
                    stringResource(R.string.about_version_value, version.name, version.code)
                )
            }
            row { UkNavRow(stringResource(R.string.licenses_open), onClick = onOpenLicenses) }
        }
    }
}
