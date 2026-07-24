# 19. Model Context Protocol (MCP) Server Integration Strategy
**Document Version:** 1.0  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  
**Author:** Senior Technical Lead & AI System Architect  

---

## 1. What is Model Context Protocol (MCP)?

**Model Context Protocol (MCP)** is an open-standard client-server protocol developed by Anthropic (using JSON-RPC 2.0 over Server-Sent Events (SSE) or stdio). It standardizes how external AI clients discover, query, and execute tools, resources, and prompts across different software platforms.

```text
  ┌─────────────────────────────────┐                 ┌──────────────────────────────────┐
  │ External AI Client              │   JSON-RPC 2.0  │ reForm MCP Server                │
  │ (Claude Desktop / Cursor IDE /  ├────────────────►│ (https://api.reform.com/mcp)     │
  │  ChatGPT / AutoGPT Agent)       │    over SSE     │  Exposes Forms, Blocks & Analytics│
  └─────────────────────────────────┘                 └──────────────────────────────────┘
```

---

## 2. The 3 Core Primitives of MCP

1. **Resources (`reform://` URI scheme):**
   * Exposes read-only data endpoints that external AIs can attach to context windows.
   * *Examples:* `reform://forms/{formId}/layout`, `reform://submissions/{formId}/analytics`.
2. **Tools (`tools/list` & `tools/call`):**
   * Executable backend functions exposed to external AIs.
   * *Examples:* `create_form_block`, `update_form_theme`, `export_submission_csv`.
3. **Prompts (`prompts/get`):**
   * Standardized prompt templates for interview creation.

---

## 3. Comparison: REST API vs. Native Tool Calling vs. MCP

| Metric | REST API | Native Tool Calling | MCP (Model Context Protocol) |
| :--- | :--- | :--- | :--- |
| **Communication** | Standard HTTP GET/POST | Internal LLM API payload | Standardized JSON-RPC 2.0 over SSE |
| **Client Type** | Web Browsers / Mobile Apps | Internal Java Backend | External AI Clients (Claude, Cursor, ChatGPT) |
| **Protocol Purpose** | Human & web app CRUD | Internal LLM function execution | Inter-AI Tool & Resource Ecosystem Sharing |

---

## 4. Commercial Advantage: Why reForm Needs an MCP Server

By exposing `https://api.reform.com/mcp`, reForm enables **Bring Your Own AI Client (BYO-AI)**:
* A recruiter inside Claude Desktop can say: *"Connect to reForm via MCP, fetch form #456, and insert a 5-star technical rating block."*
* Your `reForm MCP Server` handles the JSON-RPC request and updates the form layout securely!
