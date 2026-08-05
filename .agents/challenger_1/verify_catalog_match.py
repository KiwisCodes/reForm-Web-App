import re

DOC_PATH = "/Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/09_agent_architecture_master_design.md"

with open(DOC_PATH, "r", encoding="utf-8") as f:
    text = f.read()

# Extract Section 2 table
sec2_match = re.search(r'## 2\. Agent Catalog Table.*?\n(.*?)\n## 3\.', text, re.DOTALL)
sec2_text = sec2_match.group(1) if sec2_match else ""

catalog_rows = []
for line in sec2_text.split('\n'):
    line_s = line.strip()
    if line_s.startswith('|') and not line_s.startswith('|---') and not line_s.startswith('| :---'):
        cols = [c.strip() for c in line_s.split('|')[1:-1]]
        if len(cols) >= 4:
            name_raw = cols[0]
            if "Agent Name" in name_raw:
                continue
            
            agent_name = re.sub(r'[`\*]', '', name_raw).strip()
            catalog_rows.append({
                "name": agent_name,
                "pipeline": cols[1],
                "status": cols[2],
                "role": cols[3],
                "tech": cols[4] if len(cols) > 4 else ""
            })

# Extract Section 4 deep dives
sec4_match = re.search(r'## 4\. Per-Agent Deep-Dive Sections.*?\n(.*?)\n## 5\.', text, re.DOTALL)
sec4_text = sec4_match.group(1) if sec4_match else ""

deep_dive_blocks = re.split(r'\n(?=####\s+)', sec4_text)
deep_dive_agents = []

for block in deep_dive_blocks:
    if not block.strip().startswith('####'):
        continue
    header_line = block.strip().split('\n')[0]
    m = re.search(r'####\s*(\d+)\.\s*`?([A-Za-z0-9_]+)`?', header_line)
    if m:
        num = m.group(1)
        name = m.group(2)
        deep_dive_agents.append({"num": num, "name": name, "header": header_line})
    else:
        print(f"WARNING: Could not parse header: {header_line}")

print(f"Catalog Agents count: {len(catalog_rows)}")
print(f"Deep Dive Agents count: {len(deep_dive_agents)}")

print("\n--- 1-to-1 Mapping Check ---")
mismatch_found = False

for i in range(max(len(catalog_rows), len(deep_dive_agents))):
    c = catalog_rows[i] if i < len(catalog_rows) else None
    d = deep_dive_agents[i] if i < len(deep_dive_agents) else None
    
    c_str = f"Catalog #{i+1:02d}: {c['name']}" if c else "Catalog: NONE"
    d_str = f"DeepDive #{d['num']}: {d['name']}" if d else "DeepDive: NONE"
    
    match_status = "OK"
    if not c or not d or c['name'] != d['name'] or str(i+1) != str(d['num']):
        match_status = "MISMATCH!"
        mismatch_found = True
        
    print(f"Row {i+1:02d}: [{match_status:9s}] {c_str:45s} <==> {d_str}")

if not mismatch_found:
    print("\nVERDICT FOR TASK 1: PERFECT 1:1 MATCH! Every single agent in Section 2 Catalog matches Section 4 Deep-Dive by number and exact name!")
else:
    print("\nVERDICT FOR TASK 1: MISMATCH DETECTED!")
