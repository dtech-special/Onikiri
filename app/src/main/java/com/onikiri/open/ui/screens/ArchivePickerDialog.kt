package com.onikiri.open.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onikiri.open.R
import com.onikiri.open.data.ReleaseAsset
import com.onikiri.open.viewmodel.ArchivePickerState
import java.util.Locale

/**
 * Shown when a release has several .zip files, or when the downloaded one has no module.prop.
 * Archives that were already tried without module.prop are greyed out.
 */
@Composable
fun ArchivePickerDialog(
    picker: ArchivePickerState,
    onConfirm: (ReleaseAsset) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember(picker) { mutableStateOf(picker.selectedName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.archive_picker_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                for (asset in picker.assets) {
                    val rejected = asset.name in picker.rejected
                    val titleColor = if (rejected) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = asset.name }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == asset.name, onClick = null)
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(text = asset.name, style = MaterialTheme.typography.bodyLarge, color = titleColor)
                            val size = if (asset.size > 0) {
                                String.format(Locale.getDefault(), "%.1f MB", asset.size / (1024.0 * 1024.0))
                            } else {
                                "?"
                            }
                            Text(
                                text = if (rejected) "$size  ·  ${stringResource(R.string.archive_no_prop)}" else size,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (rejected) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null,
                onClick = { picker.assets.firstOrNull { it.name == selected }?.let(onConfirm) }
            ) { Text(stringResource(R.string.archive_choose)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_action)) }
        }
    )
}
