import re

DOC_PATH = "/Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/09_agent_architecture_master_design.md"

with open(DOC_PATH, "r", encoding="utf-8") as f:
    text = f.read()

# Find EndSessionToolHandler subsection
match = re.search(r'#### 39\.\s*`EndSessionToolHandler`.*?(?=####|\Z)', text, re.DOTALL)
if match:
    print("--- EndSessionToolHandler text ---")
    print(match.group(0))
else:
    print("EndSessionToolHandler not found!")
