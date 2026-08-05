import re
import os
import glob

DOC_PATH = "/Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/09_agent_architecture_master_design.md"
BACKEND_SRC = "/Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java"

with open(DOC_PATH, "r", encoding="utf-8") as f:
    doc_text = f.read()

# ---------------------------------------------------------
# TASK 1: Parse Section 2 (Agent Catalog Table) vs Section 4 Deep-Dives
# ---------------------------------------------------------

# Extract Section 2
sec2_match = re.search(r'## 2\. Agent Catalog Table.*?\n(.*?)\n## 3\.', doc_text, re.DOTALL)
if not sec2_match:
    print("ERROR: Section 2 not found!")
    sec2_text = ""
else:
    sec2_text = sec2_match.group(1)

# Extract table rows from Section 2
table_rows = []
for line in sec2_text.split('\n'):
    if line.strip().startswith('|') and not line.strip().startswith('|---') and not line.strip().startswith('| #') and not line.strip().startswith('| Agent Name'):
        parts = [p.strip() for p in line.strip().split('|')[1:-1]]
        if len(parts) >= 2:
            # Usually column 0 is # or Agent Name, column 1 is Agent Name or Pipeline...
            # Let's inspect raw table line
            table_rows.append(parts)

print(f"--- Section 2 Agent Catalog Table ---")
print(f"Total table rows found: {len(table_rows)}")

catalog_agents = []
for row in table_rows:
    # Look for agent name with backticks or raw
    # e.g. `LayoutAgent` or **LayoutAgent** or LayoutAgent
    row_str = " ".join(row)
    names = re.findall(r'`([A-Za-z0-9_]+)`', row_str)
    if names:
        catalog_agents.append(names[0])
    else:
        # try first non-numeric column
        for c in row:
            clean_c = c.strip('`* ')
            if clean_c and not clean_c.isdigit() and clean_c not in ["Built", "Designed", "Net-New", "Strategy", "Observer"]:
                catalog_agents.append(clean_c)
                break

print(f"Catalog agent names ({len(catalog_agents)}):")
print(catalog_agents)

# Extract Section 4 deep dives
sec4_match = re.search(r'## 4\. Per-Agent Deep-Dive Sections.*?\n(.*?)\n## 5\.', doc_text, re.DOTALL)
if not sec4_match:
    print("ERROR: Section 4 not found!")
    sec4_text = ""
else:
    sec4_text = sec4_match.group(1)

# Find all level 4 headers (#### ...) under Section 4
deep_dive_headers = re.findall(r'####\s+.*', sec4_text)
print(f"\n--- Section 4 Deep-Dive Subsections ---")
print(f"Total level 4 headers found in Sec 4: {len(deep_dive_headers)}")

deep_dive_agents = []
for h in deep_dive_headers:
    # extract backticked name or words
    names = re.findall(r'`([A-Za-z0-9_]+)`', h)
    if names:
        deep_dive_agents.append(names[0])
    else:
        # strip digits and spaces
        clean_h = re.sub(r'####\s*\d+\.\s*', '', h).strip()
        clean_h = clean_h.strip('`* ')
        deep_dive_agents.append(clean_h)

print(f"Deep dive agent names ({len(deep_dive_agents)}):")
print(deep_dive_agents)

# Check set difference
catalog_set = set(catalog_agents)
deep_dive_set = set(deep_dive_agents)

missing_in_deep_dive = catalog_set - deep_dive_set
missing_in_catalog = deep_dive_set - catalog_set

print(f"\nAgents in Catalog but missing in Deep-Dive ({len(missing_in_deep_dive)}): {missing_in_deep_dive}")
print(f"Agents in Deep-Dive but missing in Catalog ({len(missing_in_catalog)}): {missing_in_catalog}")

# ---------------------------------------------------------
# TASK 2: Verify 7 Mandatory Sub-Fields in Each Deep Dive
# ---------------------------------------------------------
print(f"\n--- Checking Mandatory 7 Sub-Fields in Deep-Dives ---")

# Split Section 4 by level 4 headers (#### ...)
deep_dive_blocks = re.split(r'\n(?=####\s+)', sec4_text)

subfield_patterns = [
    r'1\.\s*\*\*Name\s*&(?:amp;)?\s*Role\*\*',
    r'2\.\s*\*\*Trigger\s*Mechanism\*\*',
    r'3\.\s*\*\*Input\s*/\s*Output.*?\*\*',
    r'4\.\s*\*\*Design\s*Pattern\*\*',
    r'5\.\s*\*\*Technology\s*Choice\*\*',
    r'6\.\s*\*\*SOLID\s*\+\s*KISS\s*Justification\*\*',
    r'7\.\s*\*\*Open\s*Questions\*\*'
]

field_names = [
    "Name & Role",
    "Trigger Mechanism",
    "Input / Output",
    "Design Pattern",
    "Technology Choice",
    "SOLID + KISS Justification",
    "Open Questions"
]

missing_fields_per_agent = {}

for block in deep_dive_blocks:
    if not block.strip().startswith('####'):
        continue
    header_line = block.strip().split('\n')[0]
    agent_name_m = re.findall(r'`([A-Za-z0-9_]+)`', header_line)
    agent_name = agent_name_m[0] if agent_name_m else header_line
    
    missing = []
    for idx, pattern in enumerate(subfield_patterns):
        if not re.search(pattern, block, re.IGNORECASE):
            missing.append(field_names[idx])
            
    if missing:
        missing_fields_per_agent[agent_name] = missing

if missing_fields_per_agent:
    print(f"Found {len(missing_fields_per_agent)} agents with missing mandatory sub-fields:")
    for ag, miss in missing_fields_per_agent.items():
        print(f"  - {ag}: missing {miss}")
else:
    print("ALL deep-dive sections contain ALL 7 mandatory sub-fields without exception!")

