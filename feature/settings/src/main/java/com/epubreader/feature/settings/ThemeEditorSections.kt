package com.epubreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ThemeEditorSection(
    title: String,
    subtitle: String? = null,
    fields: List<ThemeEditorColorField>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ThemeEditorFieldGrid(fields = fields)
    }
}

@Composable
private fun ThemeEditorFieldGrid(fields: List<ThemeEditorColorField>) {
    fields.chunked(2).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ThemeEditorFieldCell(field = row.first(), modifier = Modifier.weight(1f))
            if (row.size == 2) {
                ThemeEditorFieldCell(field = row[1], modifier = Modifier.weight(1f))
            }
            if (row.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ThemeEditorFieldCell(
    field: ThemeEditorColorField,
    modifier: Modifier,
) {
    Box(modifier = modifier) {
        StudioColorCell(
            label = field.label,
            value = field.value,
            onClick = field.onClick,
            testTagPrefix = field.testTagPrefix,
        )
    }
}

internal data class ThemeEditorColorField(
    val label: String,
    val value: String,
    val testTagPrefix: String?,
    val onClick: () -> Unit,
)
