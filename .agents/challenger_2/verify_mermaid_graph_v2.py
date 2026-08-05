import re

def verify_mermaid_v2():
    with open('.agents/challenger_2/extracted_diagram_v2.mmd', 'r', encoding='utf-8') as f:
        lines = f.readlines()

    print("=== RE-VERIFICATION: MERMAID DIAGRAM SYNTAX & STRUCTURE ===")

    subgraph_stack = []
    subgraphs = {}
    node_declarations = {}
    all_defined_nodes = set()
    all_referenced_nodes = set()
    errors = []

    subgraph_pattern = re.compile(r'^\s*subgraph\s+([A-Za-z0-9_]+)\s*\[(.*)\]')
    end_pattern = re.compile(r'^\s*end\s*$')
    node_def_pattern = re.compile(r'^\s*([A-Za-z0-9_]+)(?:\[(.*)\]|\((.*)\)|\{\{(.*)\}\})')

    for line_num, line in enumerate(lines, 1):
        stripped = line.strip()
        if not stripped or stripped.startswith('%%'):
            continue

        sg_match = subgraph_pattern.match(stripped)
        if sg_match:
            sg_id, sg_label = sg_match.groups()
            subgraph_stack.append(sg_id)
            subgraphs[sg_id] = {'label': sg_label, 'nodes': []}
            continue

        if end_pattern.match(stripped):
            if not subgraph_stack:
                errors.append(f"Line {line_num}: Unmatched 'end' statement.")
            else:
                subgraph_stack.pop()
            continue

        # Check for edges (arrows)
        if '-->' in stripped or '<-->' in stripped or '==>' in stripped:
            # Strip edge labels |...| first to cleanly get node tokens
            line_no_labels = re.sub(r'\|[^|]+\|', ' ', stripped)
            # Find all node IDs (identifiers before optional brackets/quotes)
            tokens = re.findall(r'\b([A-Za-z0-9_]+)(?:\[[^\]]*\])?', line_no_labels)
            for token in tokens:
                if token not in ('graph', 'subgraph', 'end', 'TD', 'LR', 'BT', 'RL'):
                    all_referenced_nodes.add(token)
            continue

        nd_match = node_def_pattern.match(stripped)
        if nd_match:
            node_id = nd_match.group(1)
            content = nd_match.group(2) or nd_match.group(3) or nd_match.group(4)
            all_defined_nodes.add(node_id)
            node_declarations[node_id] = content
            if subgraph_stack:
                subgraphs[subgraph_stack[-1]]['nodes'].append(node_id)
            continue

    if subgraph_stack:
        errors.append(f"Unclosed subgraphs: {subgraph_stack}")

    print(f"Total Subgraphs found: {len(subgraphs)}")
    for sg_id, sg_data in subgraphs.items():
        print(f"  - Subgraph [{sg_id}]: '{sg_data['label']}' ({len(sg_data['nodes'])} declared nodes)")

    print(f"\nTotal Nodes defined in subgraphs/standalone: {len(all_defined_nodes)}")
    print(f"Total Nodes referenced in edge chains: {len(all_referenced_nodes)}")

    # Check for nodes defined in subgraphs but NEVER referenced in any edge chain
    unconnected_nodes = all_defined_nodes - all_referenced_nodes
    if unconnected_nodes:
        for un in sorted(unconnected_nodes):
            errors.append(f"Node '{un}' is defined in subgraph but HAS ZERO CONNECTIONS in any edge.")
    else:
        print("\n✅ ZERO ORPHANED NODES! All declared nodes are properly connected in edges.")

    # Check for raw \n string escapes in code
    raw_code = "".join(lines)
    if '\\n' in raw_code:
        errors.append("Raw \\n string escapes found in Mermaid code block!")
    else:
        print("✅ NO RAW \\n STRING ESCAPES FOUND. `<br/>` used properly.")

    if errors:
        print("\n❌ ERRORS FOUND:")
        for err in errors:
            print(f"  - {err}")
    else:
        print("\n✅ MERMAID DIAGRAM AUDIT: 100% CLEAN AND PASSED!")

    return len(errors) == 0

if __name__ == '__main__':
    verify_mermaid_v2()
