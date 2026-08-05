import re
import sys

FILE_PATH = "/Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/09_agent_architecture_master_design.md"

with open(FILE_PATH, "r", encoding="utf-8") as f:
    content = f.read()

lines = content.splitlines()
print(f"Total lines: {len(lines)}")
print(f"Total size: {len(content)} bytes")

# Check H2 sections
h2_headers = [line for line in lines if line.startswith("## ")]
print("\n--- H2 Sections Found ---")
for h in h2_headers:
    print(h)

# Check required sections
req_sections = [
    "Section 1: What is Agentic in 2026?",
    "Section 2: Agent Catalog Table",
    "Section 3: Platform Mermaid Architecture Diagram",
    "Section 4: Per-Agent Deep-Dive Sections",
    "Section 5: Technology Decision Matrix",
    "Section 6: Design Principles Summary"
]

missing_sections = []
for sec in req_sections:
    if not any(sec.lower() in h.lower() for h in h2_headers):
        missing_sections.append(sec)

print(f"\nMissing main sections: {missing_sections}")

# Extract all H4 headings (agents) under Section 4
h4_headers = [line for line in lines if line.startswith("#### ")]
print(f"\nTotal H4 Agent Deep Dives found: {len(h4_headers)}")

# Sub-field requirements:
# 1. Name & Role
# 2. Trigger Mechanism
# 3. Input / Output
# 4. Design Pattern
# 5. Technology Choice
# 6. SOLID
# 7. Open Questions

mandatory_subfields = [
    "Name & Role",
    "Trigger Mechanism",
    "Input / Output",
    "Design Pattern",
    "Technology Choice",
    "SOLID",
    "Open Questions"
]

agent_blocks = []
current_agent = None
current_content = []

for line in lines:
    if line.startswith("#### "):
        if current_agent:
            agent_blocks.append((current_agent, "\n".join(current_content)))
        current_agent = line.replace("#### ", "").strip()
        current_content = []
    elif current_agent:
        current_content.append(line)

if current_agent:
    agent_blocks.append((current_agent, "\n".join(current_content)))

print(f"Parsed {len(agent_blocks)} agent blocks.")

def audit_agent_block(name, text):
    missing_fields = []
    for sf in mandatory_subfields:
        # case insensitive regex search for subfield header like **1. Name & Role** or **Name & Role**
        if not re.search(re.escape(sf), text, re.IGNORECASE):
            missing_fields.append(sf)
    return missing_fields

agent_audit_results = {}
failed_agents = []

for name, text in agent_blocks:
    missing = audit_agent_block(name, text)
    agent_audit_results[name] = missing
    if missing:
        failed_agents.append((name, missing))

print(f"\nDeep Dives failing 7 sub-field requirement: {len(failed_agents)}")
for name, missing in failed_agents:
    print(f"  - {name}: missing {missing}")

# Check specific agent coverage requirement:
# Built: LayoutAgent, 18 IToolCallHandler strategy beans, FormAiAgentProfile
# Designed: GuardrailAgent, MemoryGoalAgent, BillingAgent, EvaluationAgent, RagSearchAgent, CodeAnalysisSubAgent, DocumentOcrSubAgent, ScoringSubAgent

required_built = [
    "LayoutAgent",
    "FormAiAgentProfile"
]
# 18 tool call handlers check
expected_18_tool_handlers = [
    "AddFieldToolHandler",
    "RemoveFieldToolHandler",
    "UpdateFieldToolHandler",
    "ReorderFieldsToolHandler",
    "UpdateFormMetadataToolHandler",
    "SetValidationRulesToolHandler",
    "ConfigureLogicRulesToolHandler",
    "ApplyThemeStylingToolHandler",
    "BulkAddFieldsToolHandler",
    "DuplicateSectionToolHandler",
    "GenerateFormTemplateToolHandler",
    "UpdateStepFlowToolHandler",
    "ConfigureIntegrationToolHandler",
    "SetAccessControlToolHandler",
    "TranslateFormToolHandler",
    "GenerateDummyDataToolHandler",
    "ValidateFormSchemaToolHandler",
    "PublishFormToolHandler"
]

required_designed = [
    "GuardrailAgent",
    "MemoryGoalAgent",
    "BillingAgent",
    "EvaluationAgent",
    "RagSearchAgent",
    "CodeAnalysisSubAgent",
    "DocumentOcrSubAgent",
    "ScoringSubAgent"
]

all_agent_names_text = " ".join([name for name, _ in agent_blocks])

print("\n--- Checking Required Agents Presence ---")
missing_built = [a for a in required_built if a.lower() not in content.lower()]
print(f"Missing required built entity/agents: {missing_built}")

missing_18_handlers = []
for h in expected_18_tool_handlers:
    if h.lower() not in content.lower():
        missing_18_handlers.append(h)
print(f"Missing 18 Tool Handler beans in doc: {missing_18_handlers}")

missing_designed = [a for a in required_designed if a.lower() not in content.lower()]
print(f"Missing required designed agents: {missing_designed}")

