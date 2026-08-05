import re
import os
import glob

DOC_PATH = "/Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/09_agent_architecture_master_design.md"
JAVA_BASE = "/Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java"

with open(DOC_PATH, "r", encoding="utf-8") as f:
    doc_text = f.read()

# Load all Java files and extract class names, packages, annotations, and definitions
java_files = glob.glob(f"{JAVA_BASE}/**/*.java", recursive=True)
java_class_map = {}

for jf in java_files:
    rel_path = os.path.relpath(jf, JAVA_BASE)
    filename = os.path.basename(jf)
    classname = filename.replace(".java", "")
    with open(jf, "r", encoding="utf-8") as f:
        content = f.read()
    
    is_record = "public record " in content or "record " in content
    is_interface = "public interface " in content or "interface " in content
    is_class = "public class " in content or "class " in content
    
    # Extract package
    pkg_m = re.search(r'package\s+([a-zA-Z0-9_\.]+);', content)
    pkg = pkg_m.group(1) if pkg_m else ""
    
    java_class_map[classname] = {
        "file": rel_path,
        "package": pkg,
        "is_record": is_record,
        "is_interface": is_interface,
        "is_class": is_class,
        "content": content
    }

print(f"Loaded {len(java_class_map)} Java classes from backend code.")

# Extract Section 4 deep dives
sec4_match = re.search(r'## 4\. Per-Agent Deep-Dive Sections.*?\n(.*?)\n## 5\.', doc_text, re.DOTALL)
sec4_text = sec4_match.group(1) if sec4_match else ""

deep_dive_blocks = re.split(r'\n(?=####\s+)', sec4_text)

print("\n--- Checking Data Type Exactness in Section 4 Deep-Dives ---")

findings = []

for block in deep_dive_blocks:
    if not block.strip().startswith('####'):
        continue
    header_line = block.strip().split('\n')[0]
    agent_name_m = re.findall(r'`([A-Za-z0-9_]+)`', header_line)
    agent_name = agent_name_m[0] if agent_name_m else header_line.strip()
    
    # Check for Java type references in Input/Output or text
    io_match = re.search(r'3\.\s*\*\*Input\s*/\s*Output.*?\*\*:\s*(.*?)(?=\n4\.|\n5\.|\Z)', block, re.DOTALL)
    io_text = io_match.group(1) if io_match else ""
    
    # Search for Java classes mentioned in doc
    mentioned_classes = re.findall(r'`([A-Z][A-Za-z0-9_<>\?, ]+)`', block)
    
    for mc in set(mentioned_classes):
        # strip generics
        clean_mc = mc.split('<')[0].strip()
        if clean_mc in java_class_map:
            info = java_class_map[clean_mc]
            # Verify record vs class claims
            if f"record `{clean_mc}`" in block or f"`record` `{clean_mc}`" in block or f"record {clean_mc}" in block:
                if not info["is_record"]:
                    findings.append({
                        "agent": agent_name,
                        "severity": "HIGH",
                        "issue": f"Doc claims `{clean_mc}` is a `record`, but actual Java file {info['file']} is NOT a record!"
                    })
            if info["is_record"] and f"class `{clean_mc}`" in block:
                findings.append({
                    "agent": agent_name,
                    "severity": "MEDIUM",
                    "issue": f"Doc calls `{clean_mc}` a `class`, but actual Java file is a Java 21 `record`."
                })

print("\nSpecific Checks on Existing Java Components:")

# Check 1: LayoutAgent
layout_sec = [b for b in deep_dive_blocks if "`LayoutAgent`" in b]
if layout_sec:
    l_text = layout_sec[0]
    print("\n1. LayoutAgent verification:")
    # Check event type mentioned
    if "FormLayoutModificationEvent" in l_text:
        print("  - Mentions FormLayoutModificationEvent: YES")
    if "formRepository.save(form)" in l_text or "FormRepository" in l_text:
        print("  - Mentions FormRepository: YES")

# Check 2: IToolCallHandler
print("\n2. IToolCallHandler verification:")
# Check method name in code vs doc
itool_code = java_class_map["IToolCallHandler"]["content"]
if "getFunctionName()" in itool_code:
    print("  - Java code method: getFunctionName()")
if "execute(WebSocketSession" in itool_code:
    print("  - Java code method: execute(WebSocketSession clientSession, JsonNode functionCall, String callId)")

# Check what tool handlers in Section 4 mention
tool_handlers_doc = [b for b in deep_dive_blocks if "ToolHandler" in b]
print(f"  - Total ToolHandler sections in doc: {len(tool_handlers_doc)}")

# Check 3: FormAiAgentProfile
print("\n3. FormAiAgentProfile verification:")
prof_code = java_class_map["FormAiAgentProfile"]["content"]
prof_sec = [b for b in deep_dive_blocks if "`FormAiAgentProfile`" in b]
if prof_sec:
    p_text = prof_sec[0]
    print("  - FormAiAgentProfile section in doc found: YES")
    # Check fields mentioned
    for field in ["modelKey", "systemPromptTemplate", "voiceName", "temperature", "byokApiKeyEncrypted", "form"]:
        if field in p_text and field in prof_code:
            print(f"    * Field `{field}` matches between doc and JPA Entity code.")
        elif field not in p_text:
            print(f"    * Field `{field}` NOT found in doc text!")

# Check 4: Spring Event Records
print("\n4. Spring Event Record Verification:")
events = ["BillingUsageEvent", "DocumentIngestionEvent", "FormLayoutModificationEvent", "GuardrailValidationEvent", "RagQueryEvent", "SessionEndedEvent"]
for ev in events:
    if ev in java_class_map:
        info = java_class_map[ev]
        print(f"  - {ev}: is_record={info['is_record']} (File: {info['file']})")
        # Check constructor parameters in code vs doc mentions
        c_text = info["content"]
        # extract record header
        rec_header_m = re.search(r'public record ' + ev + r'\s*\((.*?)\)', c_text, re.DOTALL)
        if rec_header_m:
            params = rec_header_m.group(1).strip().replace('\n', ' ')
            print(f"    Params in Java code: ({params})")

# Check 5: DTOs
print("\n5. Jackson DTO Verification:")
dtos = ["AiBlockDto", "AiConversationalBlockDto", "AiStaticBlockDto"]
for dto in dtos:
    if dto in java_class_map:
        info = java_class_map[dto]
        print(f"  - {dto}: is_class={info['is_class']} (File: {info['file']})")

