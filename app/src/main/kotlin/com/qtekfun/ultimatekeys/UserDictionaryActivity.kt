// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.core.UserWord
import com.qtekfun.ultimatekeys.core.UserWordsRepository
import com.qtekfun.ultimatekeys.core.settingsRepository
import com.qtekfun.ultimatekeys.core.userWordsRepository
import com.qtekfun.ultimatekeys.engine.SharedEngine
import com.qtekfun.ultimatekeys.languages.LanguageCatalog
import com.qtekfun.ultimatekeys.ui.UkActionRow
import com.qtekfun.ultimatekeys.ui.UkBarButton
import com.qtekfun.ultimatekeys.ui.UkChoiceRow
import com.qtekfun.ultimatekeys.ui.UkOption
import com.qtekfun.ultimatekeys.ui.UkRow
import com.qtekfun.ultimatekeys.ui.UkScreen
import com.qtekfun.ultimatekeys.ui.UkTextField
import com.qtekfun.ultimatekeys.ui.UkTheme
import com.qtekfun.ultimatekeys.ui.group
import com.qtekfun.ultimatekeys.ui.groupRows
import com.qtekfun.ultimatekeys.ui.note
import com.qtekfun.ultimatekeys.ui.section
import com.qtekfun.ultimatekeys.ui.setUkContent
import java.util.Locale

/** Add, remove, import and export the user's own words, and clear everything learned. */
class UserDictionaryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = userWordsRepository()
        val settings = settingsRepository()
        setUkContent {
            val enabled by settings.settings.collectAsState(initial = KeyboardSettings())
            Screen(
                repository,
                enabled.enabledLanguages.mapNotNull { LanguageCatalog.find(it)?.code }.distinct(),
                ::readText,
                ::writeText,
                onBack = ::finish
            )
        }
    }

    private fun readText(uri: Uri): String =
        contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()

    private fun writeText(uri: Uri, text: String) {
        contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
    }
}

/** The language's own name in the language of the screen ("Spanish" or "español"). */
private fun languageName(code: String): String {
    val shown = Locale.getDefault()
    return Locale.forLanguageTag(code).getDisplayLanguage(shown)
        .replaceFirstChar { it.titlecase(shown) }
}

@Composable
private fun Screen(
    repository: UserWordsRepository,
    languages: List<String>,
    read: (Uri) -> String,
    write: (Uri, String) -> Unit,
    onBack: () -> Unit
) {
    val words by repository.words.collectAsState()
    var chosen by rememberSaveable { mutableStateOf(languages.first()) }
    val language = chosen.takeIf { it in languages } ?: languages.first()
    var text by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importWords(repository, read(uri), language)
        }
    val exporter =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/plain")
        ) { uri ->
            if (uri != null) write(uri, repository.exportText())
        }
    val languageOptions = languages.map { UkOption(it, languageName(it)) }
    val back = stringResource(R.string.nav_back)
    val emptyText = stringResource(R.string.user_dictionary_empty)

    UkScreen(
        title = stringResource(R.string.user_dictionary_title),
        onBack = onBack,
        backText = back,
        backDescription = back
    ) {
        section(key = "add", header = R.string.user_dictionary_add) {
            row {
                UkChoiceRow(
                    title = stringResource(R.string.user_dictionary_language),
                    options = languageOptions,
                    selected = language,
                    onSelect = { chosen = it }
                )
            }
            row {
                UkTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = stringResource(R.string.user_dictionary_add),
                    singleLine = true,
                    trailing = {
                        UkBarButton(
                            stringResource(R.string.user_dictionary_add_button),
                            onClick = { if (addWord(repository, language, text)) text = "" },
                            enabled = text.isNotBlank()
                        )
                    }
                )
            }
        }
        group(key = "actions") {
            row {
                UkActionRow(stringResource(R.string.user_dictionary_import), onClick = {
                    importer.launch(arrayOf("text/*"))
                })
            }
            row {
                UkActionRow(stringResource(R.string.user_dictionary_export), onClick = {
                    exporter.launch("ultimatekeys-words.txt")
                })
            }
            row {
                UkActionRow(
                    stringResource(R.string.clear_learned),
                    onClick = { confirmClear = true },
                    destructive = true
                )
            }
        }
        if (words.isEmpty()) {
            note(emptyText, key = "empty")
        }
        groupRows(words, key = { "${it.language}:${it.word}" }) { entry ->
            val removeLabel = stringResource(R.string.user_dictionary_remove_word, entry.word)
            UkRow(
                title = entry.word,
                subtitle = languageName(entry.language),
                onClick = { removeWord(repository, entry) },
                onClickLabel = removeLabel,
                trailing = {
                    Text(
                        stringResource(R.string.user_dictionary_remove),
                        style = UkTheme.typography.body,
                        color = UkTheme.colors.destructive
                    )
                }
            )
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_learned)) },
            text = { Text(stringResource(R.string.clear_learned_message)) },
            confirmButton = {
                TextButton(onClick = {
                    SharedEngine.instance.clearLearned()
                    repository.clear()
                    confirmClear = false
                }) { Text(stringResource(R.string.clear_learned_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmClear = false
                }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }
}

private fun addWord(repository: UserWordsRepository, language: String, word: String): Boolean {
    val added = repository.add(language, word)
    if (added) {
        SharedEngine.instance.addToUserDictionary(
            word.trim(),
            Locale.forLanguageTag(language)
        )
    }
    return added
}

private fun removeWord(repository: UserWordsRepository, entry: UserWord) {
    repository.remove(entry)
    SharedEngine.instance.removeFromUserDictionary(
        entry.word,
        Locale.forLanguageTag(entry.language)
    )
}

private fun importWords(repository: UserWordsRepository, text: String, language: String) {
    val before = repository.words.value.toSet()
    repository.importText(text, language)
    (repository.words.value.toSet() - before).forEach {
        SharedEngine.instance.addToUserDictionary(it.word, Locale.forLanguageTag(it.language))
    }
}
