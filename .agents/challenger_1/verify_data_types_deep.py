import re
import os
import glob

DOC_PATH = "/Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/09_agent_architecture_master_design.md"
JAVA_BASE = "/Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java"

with open(DOC_PATH, "r", encoding="utf-8") as f:
    text = f.read()

print("--- 1. Checking method names for IToolCallHandler in doc ---")

# Check if getToolName vs getFunctionName or execute vs executeToolCall appears in doc
print("Mentions of tool handler methods in doc:")
if "getFunctionName" in text:
    print("  - getFunctionName: FOUND in doc")
if "getToolName" in text:
    print("  - getToolName: FOUND in doc")
if "execute(" in text or "executeTool" in text:
    print("  - execute / executeTool: FOUND in doc")

print("\n--- 2. Checking LayoutAgent description in doc ---")
sec4_match = re.search(r'## 4\. Per-Agent Deep-Dive Sections.*?\n(.*?)\n## 5\.', text, re.DOTALL)
sec4_text = sec4_match.group(1) if sec4_match else ""

layout_block = [b for b in re.split(r'\n(?=####\s+)', sec4_text) if "`LayoutAgent`" in b][0]
print(layout_block[:1000])

print("\n--- 3. Checking FormAiAgentProfile description in doc ---")
profile_block = [b for b in re.split(r'\n(?=####\s+)', sec4_text) if "`FormAiAgentProfile`" in b][0]
print(profile_block[:1000])

print("\n--- 4. Checking 18 Tool Handler Classes against backend Java files ---")
tool_handler_files = glob.glob(f"{JAVA_BASE}/com/reForm/backend/ai/tool/handler/**/*.java", recursive=True)
java_tool_handlers = [os.path.basename(f).replace(".java", "") for f in tool_handler_files]
print(f"Java backend tool handler files ({len(java_tool_handlers)}):")
print(sorted(java_tool_handlers))

# Extract tool handler sections from doc
doc_tool_handlers = []
for b in re.split(r'\n(?=####\s+)', sec4_text):
    if "ToolHandler" in b:
        m = re.search(r'####\s*\d+\.\s*`?([A-Za-z0-9_]+)`?', b)
        if m:
            doc_tool_handlers.append(m.group(1))

print(f"\nDoc tool handler sections ({len(doc_tool_handlers)}):")
print(sorted(doc_tool_handlers))

diff_handlers = set(java_tool_handlers) ^ set(doc_tool_handlers)
if not diff_handlers:
    print("\nALL 18 Java backend tool handlers match 18 doc tool handler sections EXACTLY!")
else:
    print(f"\nTool handler mismatch! Difference: {diff_handlers}")

