# RAG, Vector DB & Dynamic Tool Retrieval Architecture
**Document Version:** 2.1  
**Location:** `backend/knowledge/pth/week3/04_rag_vector_db_and_semantic_search.md`  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  

---

## 1. Tool Declaration Schema for Document Search (`searchUserDocument`)

When a form has attached documents, `SessionContextService` injects the `searchUserDocument` function declaration into the setup payload:

```java
public Map<String, Object> buildDocumentSearchToolDeclaration() {
    return Map.of(
        "functionDeclarations", List.of(Map.of(
            "name", "searchUserDocument",
            "description", "Queries uploaded user documents, company handbooks, or resumes to retrieve exact answers.",
            "parameters", Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                    "query", Map.of(
                        "type", "STRING",
                        "description", "The specific question or topic to search for in the user documents."
                    )
                ),
                "required", List.of("query")
            )
        ))
    );
}
```

---

## 2. In-Session Tool Response Execution Loop

```text
1. User Speaks: "What is the policy on remote work in the employee handbook?"
                                    │
                                    ▼
2. Gemini 3.1 Live API emits toolCall:
   { "toolCall": { "functionCalls": [{ "id": "call_99", "name": "searchUserDocument", "args": { "query": "remote work policy" } }] } }
                                    │
                                    ▼
3. GeminiLiveVoiceAdapter catches toolCall ──► Delegates to RagSearchAgent
                                    │
                                    ▼
4. RagSearchAgent vectorizes "remote work policy" via text-embedding-004
   Executes pgvector Cosine Distance Query (<=>) ──► Returns Top-3 Passages
                                    │
                                    ▼
5. Adapter sends toolResponse back over WebSocket:
   { "toolResponse": { "functionResponses": [{ "id": "call_99", "response": { "output": { "passages": ["Section 4.1: Remote work is permitted up to 3 days/week..."] } } }] } }
                                    │
                                    ▼
6. Gemini Live reads response passage and speaks answer out loud natively!
```

---

## 3. Database Schema (`pgvector`)

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document_embeddings (
    id UUID PRIMARY KEY,
    form_id UUID REFERENCES forms(id) ON DELETE CASCADE,
    document_name VARCHAR(255) NOT NULL,
    chunk_index INT NOT NULL,
    chunk_content TEXT NOT NULL,
    embedding vector(768) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_document_embeddings_hnsw 
ON document_embeddings 
USING hnsw (embedding vector_cosine_ops);
```
