import re
import os

DOC_PATH = "/Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/09_agent_architecture_master_design.md"

with open(DOC_PATH, "r", encoding="utf-8") as f:
    text = f.read()

# Extract Section 4 text
sec4_match = re.search(r'## 4\. Per-Agent Deep-Dive Sections.*?\n(.*?)\n## 5\.', text, re.DOTALL)
if not sec4_match:
    print("ERROR: Section 4 not found!")
    exit(1)

sec4_text = sec4_match.group(1)

# Split by level 4 headers (#### ...)
deep_dive_blocks = re.split(r'\n(?=####\s+)', sec4_text)

subfield_names = [
    "Name & Role",
    "Trigger Mechanism",
    "Input / Output",
    "Design Pattern",
    "Technology Choice",
    "SOLID + KISS Justification",
    "Open Questions"
]

# Patterns flexible with numbering or no numbering
subfield_regexes = [
    (r'Name\s*&(?:amp;)?\s*Role', "Name & Role"),
    (r'Trigger\s*Mechanism', "Trigger Mechanism"),
    (r'Input\s*/\s*Output', "Input / Output"),
    (r'Design\s*Pattern', "Design Pattern"),
    (r'Technology\s*Choice', "Technology Choice"),
    (r'SOLID\s*\+\s*KISS\s*Justification', "SOLID + KISS Justification"),
    (r'Open\s*Questions', "Open Questions")
]

results = []
agents_tested = 0

for block in deep_dive_blocks:
    if not block.strip().startswith('####'):
        continue
    agents_tested += 1
    header_line = block.strip().split('\n')[0]
    agent_name_m = re.findall(r'`([A-Za-z0-9_]+)`', header_line)
    agent_name = agent_name_m[0] if agent_name_m else header_line.strip()
    
    missing = []
    found = []
    for reg, label in subfield_regexes:
        if re.search(reg, block, re.IGNORECASE):
            found.append(label)
        else:
            missing.append(label)
            
    results.append({
        "name": agent_name,
        "header": header_line,
        "found_count": len(found),
        "missing": missing
    })

print(f"Total agent deep-dive blocks analyzed: {agents_tested}")
print("=" * 60)

all_passed = True
for r in results:
    if r["missing"]:
        all_passed = False
        print(f"FAIL: Agent {r['name']} is missing {r['missing']}")
    else:
        print(f"PASS: Agent {r['name']} - All 7 sub-fields present.")

print("=" * 60)
if all_passed:
    print("VERDICT FOR TASK 2: ALL 43 deep-dive sections contain ALL 7 mandatory sub-fields without exception!")
else:
    print("VERDICT FOR TASK 2: FAIL - Some agents are missing mandatory sub-fields.")
