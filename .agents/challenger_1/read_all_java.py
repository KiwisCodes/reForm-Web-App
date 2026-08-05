import os
import glob

JAVA_BASE = "/Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java"

def read_file(rel):
    path = os.path.join(JAVA_BASE, rel)
    with open(path, "r", encoding="utf-8") as f:
        return f.read()

print("=== LayoutAgent.java ===")
print(read_file("com/reForm/backend/ai/agent/LayoutAgent.java"))

print("\n=== ToolCallRegistry.java ===")
print(read_file("com/reForm/backend/ai/tool/registry/ToolCallRegistry.java"))

print("\n=== AiBlockDto.java ===")
print(read_file("com/reForm/backend/ai/dto/AiBlockDto.java"))

print("\n=== AiConversationalBlockDto.java ===")
print(read_file("com/reForm/backend/ai/dto/AiConversationalBlockDto.java"))

print("\n=== AiStaticBlockDto.java ===")
print(read_file("com/reForm/backend/ai/dto/AiStaticBlockDto.java"))

