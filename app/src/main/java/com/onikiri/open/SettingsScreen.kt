package com.onikiri.open

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private const val PREFS_NAME = "settings"
private const val REPO_STANDARD = "repo_standard"
private const val REPO_URL = "repo_url"

private const val STANDARD_REPO =
    "dtech-special/Onikiri/refs/heads/main/modules.jsonl"

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var standard by rememberSaveable {
        mutableStateOf(prefs.getBoolean(REPO_STANDARD, true))
    }

    var customUrl by rememberSaveable {
        mutableStateOf(prefs.getString(REPO_URL, "") ?: "")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.repo_url),
            style = MaterialTheme.typography.titleLarge
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            SegmentedButton(
                selected = standard,
                onClick = { standard = true },
                shape = SegmentedButtonDefaults.itemShape(0, 2)
            ) {
                Text(stringResource(R.string.repo_standart))
            }

            SegmentedButton(
                selected = !standard,
                onClick = { standard = false },
                shape = SegmentedButtonDefaults.itemShape(1, 2)
            ) {
                Text(stringResource(R.string.repo_custom))
            }
        }

        if (standard) {
            OutlinedTextField(
                value = STANDARD_REPO,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                readOnly = true,
                singleLine = true
            )
        } else {
            OutlinedTextField(
                value = customUrl,
                onValueChange = { customUrl = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text("USER/REPO/refs/heads/main/NAME.jsonl")
                }
            )

            Text(
                text = "${stringResource(R.string.hint)}: ${
                    stringResource(R.string.raw_url_hint)
                }",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = {
                val oldStandard = prefs.getBoolean(REPO_STANDARD, true)
                val oldUrl = prefs.getString(REPO_URL, "") ?: ""

                val newUrl = if (standard) {
                    STANDARD_REPO
                } else {
                    customUrl.trim()
                }

                val oldRepo = if (oldStandard) {
                    STANDARD_REPO
                } else {
                    oldUrl
                }

                if (oldRepo != newUrl || oldStandard != standard) {
                    context.deleteFile("modules.jsonl")
                    context.deleteFile("modules.sha")
                }

                prefs.edit()
                    .putBoolean(REPO_STANDARD, standard)
                    .putString(REPO_URL, customUrl.trim())
                    .apply()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.save))
        }
    }
}