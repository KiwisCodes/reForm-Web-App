import re

FILE_PATH = "/Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/09_agent_architecture_master_design.md"

with open(FILE_PATH, "r", encoding="utf-8") as f:
    content = f.read()

sec6 = re.search(r"## 6\. Design Principles Summary.*", content, re.DOTALL)
if sec6:
    print(sec6.group(0))
