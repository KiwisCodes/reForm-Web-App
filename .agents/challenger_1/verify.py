import re
import sys
import os

doc_path = "/Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/09_agent_architecture_master_design.md"

with open(doc_path, "r", encoding="utf-8") as f:
    lines = f.readlines()

content = "".join(lines)

print(f"Total lines: {len(lines)}")
print(f"Total bytes: {len(content)}")

# Find sections
sections = re.split(r'\n(?=#+ )', content)
print(f"Top-level sections count: {len(sections)}")
for i, sec in enumerate(sections):
    first_line = sec.strip().split('\n')[0]
    print(f"Section {i}: {first_line[:80]}")
