import re

file_path = r'c:\Users\Amon\Desktop\projects\Epub_Reader_v2\BUG_HISTORY.md'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Fix Headings: Only color the [CATEGORY] tag, not the whole title
# Before: ### #001 <span style="color:#2196F3">[STABLE] Title text</span>
# After:  ### #001 <span style="color:#2196F3">[STABLE]</span> Title text
content = re.sub(r'(###\s*#\d{3})\s*<span style="(.*?)">(\[STABLE\]|\[BETA\])(.*?)</span>', r'\1 <span style="\2">\3</span>\4', content)

# 2. Add coloring protocol to the rules section
rule_addition = """5. **Coloring Protocol**: Use inline HTML `<span style="color:...">` tags for categories and statuses to aid focus.
   - Categories: `[STABLE]` (<span style="color:#2196F3">#2196F3</span>), `[BETA]` (<span style="color:#9C27B0">#9C27B0</span>).
   - Statuses: `[RESOLVED]` (<span style="color:#4CAF50">#4CAF50</span>), `[OPEN]` (<span style="color:#F44336">#F44336</span>), `[PENDING]` (<span style="color:#FF9800">#FF9800</span>).
   - **Constraint**: Only color the text inside the brackets `[]`. Do not color the entire heading line. Do not use emojis."""

if "5. **Coloring Protocol**" not in content:
    content = content.replace(
        "4. **Format Consistency**: Use the exact format defined below, including Date Discovered, Date Fixed, Index, and Category (`[STABLE]` or `[BETA]`).",
        "4. **Format Consistency**: Use the exact format defined below, including Date Discovered, Date Fixed, Index, and Category (`[STABLE]` or `[BETA]`).\n" + rule_addition
    )

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated headings and protocol successfully")
