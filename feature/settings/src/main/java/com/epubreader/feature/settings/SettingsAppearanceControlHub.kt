package com.epubreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.CustomThemeIdPrefix

@Composable
internal fun ThemeControlHub(
    currentTheme: CustomTheme?,
    getSysFg: () -> Color,
    getPrimary: () -> Color,
    onCreate: () -> Unit,
    onData: () -> Unit,
    onModify: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onGallery: () -> Unit,
) {
    val isCustom = currentTheme?.id?.startsWith(CustomThemeIdPrefix) == true
    var showDataMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HubButton(
            icon = Icons.Default.Add,
            label = "Create",
            getSysFg = getSysFg,
            getPrimary = getPrimary,
            testTag = "create_custom_theme_button",
        ) {
            onCreate()
        }

        Box {
            HubButton(Icons.Default.DataUsage, "Data", getSysFg, getPrimary) {
                showDataMenu = true
            }
            DropdownMenu(expanded = showDataMenu, onDismissRequest = { showDataMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Import Themes") },
                    onClick = {
                        showDataMenu = false
                        onData()
                    },
                    leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                )
                if (isCustom) {
                    DropdownMenuItem(
                        text = { Text("Export This Theme") },
                        onClick = {
                            showDataMenu = false
                            onExport()
                        },
                        leadingIcon = { Icon(Icons.Default.FileUpload, null) },
                    )
                }
            }
        }

        HubButton(
            icon = Icons.Default.Edit,
            label = "Modify",
            getSysFg = getSysFg,
            getPrimary = getPrimary,
            enabled = isCustom,
        ) {
            onModify()
        }

        HubButton(Icons.Default.GridView, "Gallery", getSysFg, getPrimary) { onGallery() }
    }
}

@Composable
private fun HubButton(
    icon: ImageVector,
    label: String,
    getSysFg: () -> Color,
    getPrimary: () -> Color,
    enabled: Boolean = true,
    testTag: String? = null,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 0.8f else 0.2f
    val labelAlpha = if (enabled) 0.4f else 0.15f

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .then(testTag?.let { Modifier.testTag(it) } ?: Modifier)
                .drawBehind {
                    drawCircle(color = getSysFg().copy(alpha = 0.05f))
                },
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = getSysFg().copy(alpha = alpha),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = getSysFg().copy(alpha = labelAlpha),
            fontSize = 10.sp,
        )
    }
}
