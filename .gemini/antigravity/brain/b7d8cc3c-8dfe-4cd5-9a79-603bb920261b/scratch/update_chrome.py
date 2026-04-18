import sys

file_path = 'app/src/main/java/com/epubreader/feature/reader/ReaderScreenChrome.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
skip = False
for line in lines:
    if 'Row(' in line and 'padding(horizontal = 16.dp, vertical = 4.dp)' in lines[lines.index(line) + 2]:
        new_lines.append(line)
        skip = True
        # Insert the new hardcoded Row content
        new_lines.append("            modifier = Modifier\n")
        new_lines.append("                .fillMaxWidth()\n")
        new_lines.append("                .padding(horizontal = 16.dp, vertical = 4.dp),\n")
        new_lines.append("            verticalAlignment = Alignment.CenterVertically,\n")
        new_lines.append("            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)\n")
        new_lines.append("        ) {\n")
        new_lines.append("            // 1. Time\n")
        new_lines.append("            if (uiState.showClock) {\n")
        new_lines.append("                Text(\n")
        new_lines.append("                    text = timeText,\n")
        new_lines.append("                    style = MaterialTheme.typography.labelSmall.copy(\n")
        new_lines.append("                        color = themeColors.foreground.copy(alpha = 0.5f)\n")
        new_lines.append("                    )\n")
        new_lines.append("                )\n")
        new_lines.append("            }\n")
        new_lines.append("\n")
        new_lines.append("            // 2. Battery\n")
        new_lines.append("            if (uiState.showBattery && batteryText.isNotEmpty()) {\n")
        new_lines.append("                Row(\n")
        new_lines.append("                    verticalAlignment = Alignment.CenterVertically,\n")
        new_lines.append("                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)\n")
        new_lines.append("                ) {\n")
        new_lines.append("                    Icon(\n")
        new_lines.append("                        imageVector = Icons.Filled.BatteryFull,\n")
        new_lines.append("                        contentDescription = null,\n")
        new_lines.append("                        modifier = Modifier.size(12.dp),\n")
        new_lines.append("                        tint = themeColors.foreground.copy(alpha = 0.5f)\n")
        new_lines.append("                    )\n")
        new_lines.append("                    Text(\n")
        new_lines.append("                        text = batteryText,\n")
        new_lines.append("                        style = MaterialTheme.typography.labelSmall.copy(\n")
        new_lines.append("                            color = themeColors.foreground.copy(alpha = 0.5f)\n")
        new_lines.append("                        )\n")
        new_lines.append("                    )\n")
        new_lines.append("                }\n")
        new_lines.append("            }\n")
        new_lines.append("\n")
        new_lines.append("            // 3. Chapter Number\n")
        new_lines.append("            if (chapterText.isNotEmpty()) {\n")
        new_lines.append("                Text(\n")
        new_lines.append("                    text = chapterText,\n")
        new_lines.append("                    style = MaterialTheme.typography.labelSmall.copy(\n")
        new_lines.append("                        color = themeColors.foreground.copy(alpha = 0.5f)\n")
        new_lines.append("                    )\n")
        new_lines.append("                )\n")
        new_lines.append("            }\n")
        new_lines.append("\n")
        new_lines.append("            // 4. Progress\n")
        new_lines.append("            if (uiState.showChapterProgress && progressPercentage != null) {\n")
        new_lines.append("                Text(\n")
        new_lines.append("                    text = \"${(progressPercentage * 100).toInt()}%\",\n")
        new_lines.append("                    style = MaterialTheme.typography.labelSmall.copy(\n")
        new_lines.append("                        color = themeColors.foreground.copy(alpha = 0.5f)\n")
        new_lines.append("                    )\n")
        new_lines.append("                )\n")
        new_lines.append("            }\n")
        new_lines.append("        }\n")
    elif skip:
        if '}' in line and line.strip() == '}':
            # Check if this is the end of the Row block
            # This logic is a bit fragile but should work for the specific block
            # Actually, I'll just look for the end of the ReaderStatusOverlay function or next block
            pass
        if 'AnimatedVisibility' in line or 'chapterText' in line: # Logic to stop skipping
            # Actually, I'll just use a more robust way to find the end
            pass
    else:
        new_lines.append(line)

# Re-implementing with a safer range-based replacement
with open(file_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

start_idx = -1
end_idx = -1
for i, line in enumerate(lines):
    if 'Row(' in line and i + 2 < len(lines) and 'padding(horizontal = 16.dp, vertical = 4.dp)' in lines[i+2]:
        start_idx = i
    if start_idx != -1 and i > start_idx and line.strip() == '}':
        # Find the matching closing brace for the Row
        # For simplicity in this specific file, the Row ends at line 644 before the AnimatedVisibility closing brace
        if i + 1 < len(lines) and lines[i+1].strip() == '}':
             end_idx = i
             break

if start_idx != -1 and end_idx != -1:
    content = lines[:start_idx]
    content.append("        Row(\n")
    content.append("            modifier = Modifier\n")
    content.append("                .fillMaxWidth()\n")
    content.append("                .padding(horizontal = 16.dp, vertical = 4.dp),\n")
    content.append("            verticalAlignment = Alignment.CenterVertically,\n")
    content.append("            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)\n")
    content.append("        ) {\n")
    content.append("            // 1. Time\n")
    content.append("            if (uiState.showClock) {\n")
    content.append("                Text(\n")
    content.append("                    text = timeText,\n")
    content.append("                    style = MaterialTheme.typography.labelSmall.copy(\n")
    content.append("                        color = themeColors.foreground.copy(alpha = 0.5f)\n")
    content.append("                    )\n")
    content.append("                )\n")
    content.append("            }\n")
    content.append("\n")
    content.append("            // 2. Battery\n")
    content.append("            if (uiState.showBattery && batteryText.isNotEmpty()) {\n")
    content.append("                Row(\n")
    content.append("                    verticalAlignment = Alignment.CenterVertically,\n")
    content.append("                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)\n")
    content.append("                ) {\n")
    content.append("                    Icon(\n")
    content.append("                        imageVector = Icons.Filled.BatteryFull,\n")
    content.append("                        contentDescription = null,\n")
    content.append("                        modifier = Modifier.size(12.dp),\n")
    content.append("                        tint = themeColors.foreground.copy(alpha = 0.5f)\n")
    content.append("                    )\n")
    content.append("                    Text(\n")
    content.append("                        text = batteryText,\n")
    content.append("                        style = MaterialTheme.typography.labelSmall.copy(\n")
    content.append("                            color = themeColors.foreground.copy(alpha = 0.5f)\n")
    content.append("                        )\n")
    content.append("                    )\n")
    content.append("                }\n")
    content.append("            }\n")
    content.append("\n")
    content.append("            // 3. Chapter Number\n")
    content.append("            if (chapterText.isNotEmpty()) {\n")
    content.append("                Text(\n")
    content.append("                    text = chapterText,\n")
    content.append("                    style = MaterialTheme.typography.labelSmall.copy(\n")
    content.append("                        color = themeColors.foreground.copy(alpha = 0.5f)\n")
    content.append("                    )\n")
    content.append("                )\n")
    content.append("            }\n")
    content.append("\n")
    content.append("            // 4. Progress\n")
    content.append("            if (uiState.showChapterProgress && progressPercentage != null) {\n")
    content.append("                Text(\n")
    content.append("                    text = \"${(progressPercentage * 100).toInt()}%\",\n")
    content.append("                    style = MaterialTheme.typography.labelSmall.copy(\n")
    content.append("                        color = themeColors.foreground.copy(alpha = 0.5f)\n")
    content.append("                    )\n")
    content.append("                )\n")
    content.append("            }\n")
    content.append("        }\n")
    content.extend(lines[end_idx+1:])
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.writelines(content)
