import sys
import re

file_path = '/Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/09_agent_architecture_master_design.md'

with open(file_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

in_mermaid = False
mermaid_lines = []
mermaid_line_start = 0

for idx, line in enumerate(lines, 1):
    if line.strip() == '```mermaid':
        in_mermaid = True
        mermaid_line_start = idx + 1
        continue
    elif line.strip() == '```' and in_mermaid:
        in_mermaid = False
        break
    if in_mermaid:
        mermaid_lines.append(line)

print(f"Mermaid diagram starts at line {mermaid_line_start}, total lines: {len(mermaid_lines)}")
mermaid_code = "".join(mermaid_lines)

with open('/Users/apple/Coding-projects/reForm-Web-App/.agents/challenger_2/extracted_diagram_v2.mmd', 'w', encoding='utf-8') as f:
    f.write(mermaid_code)

print("Saved updated diagram to .agents/challenger_2/extracted_diagram_v2.mmd")
