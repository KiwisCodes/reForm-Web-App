import os
import glob
import re

JAVA_BASE = "/Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java"

java_files = glob.glob(f"{JAVA_BASE}/**/*.java", recursive=True)
print(f"Total Java files found: {len(java_files)}")

# Print relative paths of all Java files
for f in sorted(java_files):
    rel = os.path.relpath(f, JAVA_BASE)
    print(f" - {rel}")

