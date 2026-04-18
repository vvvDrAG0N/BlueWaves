import sys

file_path = 'app/src/main/java/com/epubreader/data/settings/SettingsManager.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if 'readerStatusShowMaxChapter' not in line:
        new_lines.append(line)

with open(file_path, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
