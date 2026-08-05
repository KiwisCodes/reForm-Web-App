import re

def verify_mermaid():
    with open('.agents/challenger_2/extracted_diagram.mmd', 'r', encoding='utf-8') as f:
        lines = f.readlines()

    print("=== MERMAID DIAGRAM SYNTAX & STRUCTURE AUDIT ===")

    # 1. Check Subgraphs
    subgraph_stack = []
    subgraphs = {}
    current_subgraph = None
    node_declarations = {}
    edges = []

    subgraph_pattern = re.compile(r'^\s*subgraph\s+([A-Za-z0-9_]+)\s*\[(.*)\]')
    end_pattern = re.compile(r'^\s*end\s*$')
    # Edge patterns: node1 --> node2, node1 <--> node2, node1 -->|label| node2, node1 <-->|label| node2
    edge_pattern = re.compile(r'^\s*([A-Za-z0-9_]+)\s*(<-->|-->|<==>|==>)(?:\|([^|]+)\|)?\s*([A-Za-z0-9_]+)(?:\[(.*)\])?')
    # Node def pattern: node_id[...] or node_id(...) or node_id[("...")]
    node_def_pattern = re.compile(r'^\s*([A-Za-z0-9_]+)(?:\[(.*)\]|\((.*)\)|\{\{(.*)\}\})')

    all_defined_nodes = set()
    all_referenced_nodes = set()
    errors = []
    warnings = []

    for line_num, line in enumerate(lines, 1):
        stripped = line.strip()
        if not stripped or stripped.startswith('%%'):
            continue

        # Check subgraph
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

        # Check edge
        edge_match = edge_pattern.match(stripped)
        if edge_match:
            src, arrow, label, dst, dst_inline_def = edge_match.groups()
            edges.append({'line': line_num, 'src': src, 'arrow': arrow, 'label': label, 'dst': dst, 'raw': stripped})
            all_referenced_nodes.add(src)
            all_referenced_nodes.add(dst)
            if dst_inline_def:
                all_defined_nodes.add(dst)
                node_declarations[dst] = dst_inline_def
                if subgraph_stack:
                    subgraphs[subgraph_stack[-1]]['nodes'].append(dst)
            continue

        # Check node declaration inside subgraph/standalone
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

    print(f"\nTotal Nodes defined: {len(all_defined_nodes)}")
    print(f"Total Nodes referenced in edges: {len(all_referenced_nodes)}")
    print(f"Total Edges: {len(edges)}")

    # Check for undefined nodes referenced in edges
    undefined_nodes = all_referenced_nodes - all_defined_nodes
    if undefined_nodes:
        for un in undefined_nodes:
            errors.append(f"Node '{un}' is referenced in edges but NEVER defined in subgraphs or standalone node declarations.")

    if errors:
        print("\n❌ ERRORS FOUND:")
        for err in errors:
            print(f"  - {err}")
    else:
        print("\n✅ NO STRUCTURAL PARSING ERRORS IN MERMAID CODE.")

    return subgraphs, node_declarations, edges, errors, warnings

if __name__ == '__main__':
    verify_mermaid()
