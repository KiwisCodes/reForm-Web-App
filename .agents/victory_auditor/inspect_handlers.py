import re

FILE_PATH = "/Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/09_agent_architecture_master_design.md"

with open(FILE_PATH, "r", encoding="utf-8") as f:
    content = f.read()

lines = content.splitlines()

print("--- ALL H4 HEADERS ---")
h4s = [line for line in lines if line.startswith("#### ")]
for idx, h in enumerate(h4s, 1):
    print(f"{idx:02d}. {h}")

print("\n--- SEARCHING FOR TOOL HANDLER / STRATEGY BEANS ---")
tool_lines = [line for line in lines if "tool" in line.lower() or "handler" in line.lower() or "strategy" in line.lower()]
print(f"Total matching lines: {len(tool_lines)}")

# Print lines mentioning IToolCallHandler or ToolCall
tool_matches = [line for line in lines if "IToolCallHandler" in line or "ToolCall" in line]
for m in tool_matches[:20]:
    print(m)
