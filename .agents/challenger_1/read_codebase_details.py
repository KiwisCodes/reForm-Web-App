import os
import glob
import re

JAVA_BASE = "/Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java"

def get_file_content(rel_path):
    full_path = os.path.join(JAVA_BASE, rel_path)
    if os.path.exists(full_path):
        with open(full_path, "r", encoding="utf-8") as f:
            return f.read()
    return None

print("=== 1. LayoutAgent.java ===")
layout_code = get_file_content("com/reForm/backend/ai/agent/LayoutAgent.java")
print(layout_code[:800] if layout_code else "NOT FOUND")

print("\n=== 2. IToolCallHandler.java ===")
tool_handler_code = get_file_content("com/reForm/backend/ai/tool/port/IToolCallHandler.java")
print(tool_handler_code if tool_handler_code else "NOT FOUND")

print("\n=== 3. FormAiAgentProfile.java ===")
profile_code = get_file_content("com/reForm/backend/form/entity/FormAiAgentProfile.java")
print(profile_code if profile_code else "NOT FOUND")

print("\n=== 4. Event classes in com/reForm/backend/ai/event/ ===")
event_dir = os.path.join(JAVA_BASE, "com/reForm/backend/ai/event")
for ef in sorted(glob.glob(f"{event_dir}/*.java")):
    print(f"--- {os.path.basename(ef)} ---")
    with open(ef, "r", encoding="utf-8") as f:
        print(f.read())

