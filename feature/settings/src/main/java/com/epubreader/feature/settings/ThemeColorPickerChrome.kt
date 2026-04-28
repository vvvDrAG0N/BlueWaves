package com.epubreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun ThemeColorPickerHeader(
    label: String,
    testTagPrefix: String?,
    onClose: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(40.dp)
                .then(
                    if (testTagPrefix != null) {
                        Modifier.testTag("${testTagPrefix}_picker_close")
                    } else {
                        Modifier
                    },
                ),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
            )
        }
        Text(
            text = "$label Color",
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            textAlign = TextAlign.Start,
        )
        IconButton(
            onClick = onSave,
            modifier = Modifier
                .size(40.dp)
                .then(
                    if (testTagPrefix != null) {
                        Modifier.testTag("${testTagPrefix}_picker_save")
                    } else {
                        Modifier
                    },
                ),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Save",
            )
        }
    }
}

@Composable
internal fun ThemeColorPickerExitDialog(
    testTagPrefix: String?,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text("Save changes?") },
        confirmButton = {
            TextButton(
                onClick = onSave,
                modifier = if (testTagPrefix != null) {
                    Modifier.testTag("${testTagPrefix}_picker_exit_save")
                } else {
                    Modifier
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onDiscard,
                    modifier = if (testTagPrefix != null) {
                        Modifier.testTag("${testTagPrefix}_picker_exit_discard")
                    } else {
                        Modifier
                    },
                ) {
                    Text("Discard")
                }
                TextButton(
                    onClick = onKeepEditing,
                    modifier = if (testTagPrefix != null) {
                        Modifier.testTag("${testTagPrefix}_picker_exit_keep_editing")
                    } else {
                        Modifier
                    },
                ) {
                    Text("Keep editing")
                }
            }
        },
        modifier = if (testTagPrefix != null) {
            Modifier.testTag("${testTagPrefix}_picker_exit_dialog")
        } else {
            Modifier
        },
    )
}
