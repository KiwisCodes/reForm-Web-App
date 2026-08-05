import re
import sys

FILE_PATH = "/Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/09_agent_architecture_master_design.md"

with open(FILE_PATH, "r", encoding="utf-8") as f:
    content = f.read()

lines = content.splitlines()

# 1. Main Sections Check
sections = [
    "## 1. What is Agentic in 2026?",
    "## 2. Agent Catalog Table",
    "## 3. Platform Mermaid Architecture Diagram",
    "## 4. Per-Agent Deep-Dive Sections",
    "## 5. Technology Decision Matrix",
    "## 6. Design Principles Summary"
]

print("=== CHECKING MAIN SECTIONS ===")
for sec in sections:
    present = any(line.startswith(sec) for line in lines)
    print(f"[{'PASS' if present else 'FAIL'}] {sec}")

# 2. Section 4 Agent Deep Dives
# Find all lines starting with #### <digit>
h4_lines = [(i, line) for i, line in enumerate(lines) if line.startswith("#### ") and re.match(r"^####\s*\d+\.", line)]

print(f"\nFound {len(h4_lines)} H4 Agent Deep Dive headings.")

agent_blocks = []
for idx, (line_num, heading) in enumerate(h4_lines):
    # start line for this agent
    start = line_num
    # end line is line_num of next agent, or start of Section 5, or end of text
    if idx + 1 < len(h4_lines):
        end = h4_lines[idx + 1][0]
    else:
        # find where Section 5 starts
        end = len(lines)
        for i in range(start, len(lines)):
            if lines[i].startswith("## 5."):
                end = i
                break
    
    agent_text = "\n".join(lines[start:end])
    # Clean agent name
    name_match = re.search(r"^####\s*\d+\.\s*[`*]*(.*?)[`*]*$", heading)
    agent_name = name_match.group(1).strip() if name_match else heading
    agent_blocks.append((agent_name, agent_text, heading))

print(f"Extracted {len(agent_blocks)} agent deep dive blocks.")

# 3. Check 7 mandatory sub-fields per deep dive
mandatory_fields = [
    ("1. Name & Role", r"Name & Role"),
    ("2. Trigger Mechanism", r"Trigger Mechanism"),
    ("3. Input / Output", r"Input / Output"),
    ("4. Design Pattern", r"Design Pattern"),
    ("5. Technology Choice", r"Technology Choice"),
    ("6. SOLID + KISS Justification", r"SOLID"),
    ("7. Open Questions", r"Open Questions")
]

print("\n=== AUDITING 7 MANDATORY SUB-FIELDS PER AGENT DEEP DIVE ===")
failed = []
for name, text, heading in agent_blocks:
    missing = []
    for label, pattern in mandatory_fields:
        if not re.search(pattern, text, re.IGNORECASE):
            missing.append(label)
    if missing:
        failed.append((name, missing))
        print(f"[FAIL] {name}: missing {missing}")

if not failed:
    print(f"[PASS] All {len(agent_blocks)} agent deep dives contain ALL 7 mandatory sub-fields!")

# 4. Catalog Table Agents vs Deep Dives
catalog_section = re.search(r"## 2\. Agent Catalog Table.*?(?=## 3\.)", content, re.DOTALL)
catalog_names = []
if catalog_section:
    table_lines = catalog_section.group(0).splitlines()
    for line in table_lines:
        if line.startswith("|") and not line.startswith("| Name") and not line.startswith("| Agent Name") and not line.startswith("|---") and not line.startswith("| :---"):
            cols = [c.strip() for c in line.split("|")[1:-1]]
            if cols:
                name_clean = re.sub(r"[`*]", "", cols[0])
                name_clean = re.sub(r"^\d+\.\s*", "", name_clean).strip()
                if name_clean:
                    catalog_names.append(name_clean)

deep_dive_names = [a[0] for a in agent_blocks]

print(f"\nCatalog Table Agents: {len(catalog_names)}")
print(f"Deep Dive Agents: {len(deep_dive_names)}")

diff_cat_dd = set(catalog_names) ^ set(deep_dive_names)
print(f"Discrepancies between Catalog Table & Deep Dives: {diff_cat_dd}")

# 5. Required Agents Verification
built_agents = ["LayoutAgent", "FormAiAgentProfile"]
tool_handlers = [
    "ConfigureFillerPersonaToolHandler",
    "ModifyFormLayoutToolHandler",
    "PublishFormToolHandler",
    "GenerateContentFromDocToolHandler",
    "EvaluateResponseToolHandler",
    "FlagForHumanReviewToolHandler",
    "LookupFormProgressToolHandler",
    "SaveFieldResponseToolHandler",
    "SkipQuestionToolHandler",
    "AnalyzeUploadedFileToolHandler",
    "ExtractStructuredDataToolHandler",
    "RequestFileUploadToolHandler",
    "SaveAudioRecordingToolHandler",
    "SaveSessionTranscriptToolHandler",
    "EndSessionToolHandler",
    "SearchUserDocumentToolHandler",
    "RenderDynamicUIToolHandler",
    "SendNotificationToolHandler"
]
designed_agents = [
    "GuardrailAgent",
    "MemoryGoalAgent",
    "BillingAgent",
    "EvaluationAgent",
    "RagSearchAgent",
    "CodeAnalysisSubAgent",
    "DocumentOcrSubAgent",
    "ScoringSubAgent"
]

all_required = built_agents + tool_handlers + designed_agents

print("\n=== CHECKING PRESENCE OF ALL REQUIRED AGENTS ===")
missing_req = [a for a in all_required if a not in deep_dive_names]
if missing_req:
    print(f"[FAIL] Missing required agents: {missing_req}")
else:
    print(f"[PASS] All {len(all_required)} required built/designed agents are present in deep dives!")

print(f"\nAgent Count Summary:")
print(f"  - Total Agents Deep Dived: {len(deep_dive_names)}")
print(f"  - Built Agents & Tool Handlers: {len(built_agents) + len(tool_handlers)}")
print(f"  - Designed Agents & Sub-Agents: {len(designed_agents)}")
print(f"  - Net-New Discovered Agents: {len(deep_dive_names) - len(built_agents) - len(tool_handlers) - len(designed_agents)}")

