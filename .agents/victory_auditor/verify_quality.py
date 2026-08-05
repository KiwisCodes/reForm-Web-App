import re

FILE_PATH = "/Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/09_agent_architecture_master_design.md"

with open(FILE_PATH, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Section 1 check
sec1 = re.search(r"## 1\. What is Agentic in 2026\?.*?(?=## 2\.)", content, re.DOTALL)
if sec1:
    s1_text = sec1.group(0)
    print("=== SECTION 1 ANALYSIS ===")
    print(f"Length: {len(s1_text)} chars, {len(s1_text.splitlines())} lines")
    print("Keywords check:")
    for kw in ["@Component", "Spring", "Java 21", "pillar", "paradigm", "autonomous", "ReAct", "Tool Calling", "Memory"]:
        print(f"  - '{kw}': {'FOUND' if kw.lower() in s1_text.lower() else 'MISSING'}")

# 2. Section 3 check (Mermaid Diagram)
sec3 = re.search(r"## 3\. Platform Mermaid Architecture Diagram.*?(?=## 4\.)", content, re.DOTALL)
if sec3:
    s3_text = sec3.group(0)
    print("\n=== SECTION 3 ANALYSIS ===")
    print(f"Length: {len(s3_text)} chars, {len(s3_text.splitlines())} lines")
    mermaid_blocks = re.findall(r"```mermaid(.*?)```", s3_text, re.DOTALL)
    print(f"Mermaid blocks count: {len(mermaid_blocks)}")
    if mermaid_blocks:
        m_code = mermaid_blocks[0]
        print(f"Mermaid code lines: {len(m_code.splitlines())}")
        subgraphs = re.findall(r"subgraph\s+([^\n]+)", m_code)
        print(f"Subgraphs count: {len(subgraphs)}: {subgraphs}")

# 3. Section 5 check (Technology Decision Matrix)
sec5 = re.search(r"## 5\. Technology Decision Matrix.*?(?=## 6\.)", content, re.DOTALL)
if sec5:
    s5_text = sec5.group(0)
    print("\n=== SECTION 5 ANALYSIS ===")
    print(f"Length: {len(s5_text)} chars, {len(s5_text.splitlines())} lines")
    tables = [line for line in s5_text.splitlines() if line.startswith("|")]
    print(f"Matrix table rows count: {len(tables)}")

# 4. Section 6 check (Design Principles Summary)
sec6 = re.search(r"## 6\. Design Principles Summary.*", content, re.DOTALL)
if sec6:
    s6_text = sec6.group(0)
    print("\n=== SECTION 6 ANALYSIS ===")
    print(f"Length: {len(s6_text)} chars, {len(s6_text.splitlines())} lines")
    for kw in ["SOLID", "Single Responsibility", "Open/Closed", "Liskov", "Interface Segregation", "Dependency Inversion", "OOP", "KISS", "YAGNI"]:
        print(f"  - '{kw}': {'FOUND' if kw.lower() in s6_text.lower() else 'MISSING'}")

