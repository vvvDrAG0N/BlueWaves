import sys

file_path = 'app/src/main/java/com/epubreader/feature/reader/ReaderScreenChrome.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
skip = 0
for i, line in enumerate(lines):
    if 'val chapterText = remember(uiState.showChapterNumber' in line:
        new_lines.append('    val chapterText = remember(uiState.showChapterNumber, chapterIndex) {\n')
        new_lines.append('        if (uiState.showChapterNumber) "CH $chapterIndex" else ""\n')
        new_lines.append('    }\n')
        skip = 7 # Skip the old calculation block (approx 7 lines)
    elif skip > 0:
        skip -= 1
    else:
        new_lines.append(line)

with open(file_path, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
