// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatekeys.core.UserWord
import com.qtekfun.ultimatekeys.core.UserWordsRepository
import com.qtekfun.ultimatekeys.core.userWordsRepository
import com.qtekfun.ultimatekeys.engine.SharedEngine
import java.util.Locale

/** Add, remove, import and export the user's own words, and clear everything learned. */
class UserDictionaryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = userWordsRepository()
        setContent {
            val scheme = if (isSystemInDarkTheme()) {
                dynamicDarkColorScheme(
                    this
                )
            } else {
                dynamicLightColorScheme(this)
            }
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) { Screen(repository, ::readText, ::writeText) }
            }
        }
    }

    private fun readText(uri: Uri): String =
        contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()

    private fun writeText(uri: Uri, text: String) {
        contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
    }
}

private val languages = listOf("es", "en")

/** The language's own name in the language of the screen ("Spanish" or "español"). */
private fun languageName(code: String): String {
    val shown = Locale.getDefault()
    return Locale.forLanguageTag(code).getDisplayLanguage(shown)
        .replaceFirstChar { it.titlecase(shown) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Screen(
    repository: UserWordsRepository,
    read: (Uri) -> String,
    write: (Uri, String) -> Unit
) {
    val words by repository.words.collectAsState()
    var language by remember { mutableStateOf(languages.first()) }
    var text by remember { mutableStateOf("") }
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

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Heading(
            stringResource(R.string.user_dictionary_title),
            MaterialTheme.typography.headlineSmall
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            languages.forEach { code ->
                FilterChip(selected = language == code, onClick = {
                    language = code
                }, label = { Text(languageName(code)) })
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.user_dictionary_add)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { if (addWord(repository, language, text)) text = "" }) {
                Text(stringResource(R.string.user_dictionary_add_button))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                importer.launch(arrayOf("text/*"))
            }) { Text(stringResource(R.string.user_dictionary_import)) }
            OutlinedButton(onClick = {
                exporter.launch("ultimatekeys-words.txt")
            }) { Text(stringResource(R.string.user_dictionary_export)) }
            OutlinedButton(onClick = {
                confirmClear = true
            }) { Text(stringResource(R.string.clear_learned)) }
        }
        if (words.isEmpty()) Text(stringResource(R.string.user_dictionary_empty))
        LazyColumn(Modifier.weight(1f)) {
            items(words, key = { "${it.language}:${it.word}" }) { entry ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${entry.word}  (${languageName(entry.language)})", Modifier.weight(1f))
                    val removeLabel =
                        stringResource(R.string.user_dictionary_remove_word, entry.word)
                    TextButton(
                        onClick = { removeWord(repository, entry) },
                        modifier = Modifier.semantics { contentDescription = removeLabel }
                    ) { Text(stringResource(R.string.user_dictionary_remove)) }
                }
            }
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
