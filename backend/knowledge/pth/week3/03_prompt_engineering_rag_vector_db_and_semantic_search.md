# Prompt Engineering, RAG, Vector DB & Semantic Search Architecture
**Document Version:** 1.0  
**Location:** `backend/knowledge/pth/week3/03_prompt_engineering_rag_vector_db_and_semantic_search.md`  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  

---

## 1. Prompt Storage, Variable Hydration & Context Engineering

### A. Database Entity: `FormAiAgentProfile.java`
```java
package com.reForm.backend.form.entity;

import com.reForm.backend.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "form_ai_agent_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormAiAgentProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_id")
    private Form form;

    @Column(name = "model_key", nullable = false, length = 50)
    private String modelKey; // e.g. "GEMINI_3_1_LIVE"

    @Column(name = "system_prompt_template", columnDefinition = "TEXT")
    private String systemPromptTemplate;

    @Column(name = "voice_name", length = 50)
    private String voiceName; // "Puck", "Kore"

    @Column(name = "temperature")
    private Float temperature;

    @Column(name = "byok_api_key_encrypted", length = 512)
    private String byokApiKeyEncrypted;
}
```

### B. Prompt Template Compilation Pipeline
System prompts are stored as templates with placeholders (`{{formTitle}}`, `{{persona}}`, `{{goals}}`):

```java
public String compilePrompt(FormAiAgentProfile profile, ConversationalBlock block, Map<String, String> variables) {
    String template = (profile != null && profile.getSystemPromptTemplate() != null)
            ? profile.getSystemPromptTemplate()
            : "You are an AI Interviewer evaluating {{formTitle}}. Goals: {{goals}}";

    for (Map.Entry<String, String> entry : variables.entrySet()) {
        template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
    }
    return template;
}
```

---

## 2. Document RAG (Retrieval-Augmented Generation) Architecture

When a Form Builder uploads job descriptions, company policy PDFs, or candidate rubrics:

```text
┌───────────────────────────┐
│ User Uploads Document     │ (PDF, DOCX, TXT)
└───────────────────────────┘
              │
              ▼
┌───────────────────────────┐
│ Chunking Engine           │ Chunk Size: 512 Tokens, Overlap: 64 Tokens
└───────────────────────────┘
              │
              ▼
┌───────────────────────────┐
│ Embedding Model           │ text-embedding-004 (768 dimensions)
└───────────────────────────┘
              │
              ▼
┌───────────────────────────┐
│ PostgreSQL pgvector DB    │ Vector Index: HNSW (Hierarchical Navigable Small World)
└───────────────────────────┘
              │
              ▼
┌───────────────────────────┐
│ Hybrid Semantic Search    │ Vector Cosine Distance + Full-Text Search (tsvector)
└───────────────────────────┘
              │
              ▼
┌───────────────────────────┐
│ Inject Context into AI    │ Injected into Gemini Live Setup Payload / System Context
└───────────────────────────┘
```

### PostgreSQL `pgvector` Schema
```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document_embeddings (
    id UUID PRIMARY KEY,
    form_id UUID REFERENCES forms(id) ON DELETE CASCADE,
    chunk_content TEXT NOT NULL,
    embedding vector(768) NOT NULL
);

CREATE INDEX idx_document_embeddings_hnsw 
ON document_embeddings 
USING hnsw (embedding vector_cosine_ops);
```

---

## 3. Best Practices Matrix

| Technique | Implementation Strategy | Purpose |
| :--- | :--- | :--- |
| **Chunking** | Fixed-size 512 tokens with 64-token overlap | Prevents split sentences while keeping embeddings focused. |
| **Embedding** | Google `text-embedding-004` (768d) | Microsecond vectorization of text chunks. |
| **Vector Search** | `pgvector` with HNSW Index | Sub-5ms cosine similarity retrieval over millions of embeddings. |
| **Hybrid Search** | Cosine distance + PostgreSQL `tsvector` | Combines semantic meaning search with exact keyword matching. |
