# Document Ingestion, Storage, OCR Preprocessing & Interaction Architecture
**Document Version:** 2.0 (Live Multimodal Interaction Specification)  
**Location:** `backend/knowledge/pth/week3/08_document_ocr_preprocessing_and_storage.md`  
**Target System:** reForm Platform (`com.reForm.backend.ai`)  

---

## 1. How Users Interact With Documents Across Modes 2, 3, & 4

### Can We Send Raw PDF/DOCX Files Directly to Gemini Live API?
**NO.** Google's Gemini Multimodal Live API (`BidiGenerateContent`) over WebSockets only accepts:
1. Real-time 16-bit PCM Audio (`audio/pcm`)
2. JPEG/PNG Video Frames (`image/jpeg`)
3. Plain Text Input (`realtimeInput.text`)

Raw 50MB PDF binaries or complex DOCX files **cannot** be streamed directly over the live voice WebSocket.

---

## 2. The 2 Production Document Interaction Strategies

To enable seamless voice/text conversations about uploaded documents in Modes 2, 3, and 4, reForm implements a **Dual Interaction Strategy**:

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│ STRATEGY 1: PRE-SESSION SYSTEM CONTEXT INJECTION (Small Documents <= 4k Tokens)│
│                                                                             │
│ 1. User attaches resume or job description (PDF/DOCX)                        │
│ 2. OCR / Tika preprocessors extract clean text                              │
│ 3. SessionContextService injects text directly into Gemini Setup Payload    │
│    systemInstruction: "DOCUMENT CONTEXT:\n" + extractedText                 │
│ 4. Gemini Live has full knowledge of document from millisecond zero!        │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│ STRATEGY 2: IN-SESSION FUNCTION CALLING RAG TOOL (Large Documents > 4k Tokens)│
│                                                                             │
│ 1. Large handbook/policy chunked & indexed in pgvector                      │
│ 2. SessionContextService registers tool: searchUserDocument(query)          │
│ 3. User asks out loud: "What does section 4 say about expense refunds?"     │
│ 4. Gemini Live issues toolCall: searchUserDocument({ query: "expense refund"})│
│ 5. GeminiLiveVoiceAdapter catches toolCall ──► Queries pgvector for top-3   │
│ 6. Adapter sends toolResponse back to Gemini WSS                            │
│ 7. Gemini Live speaks out loud answering the question natively!             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Interaction Specs Across All Modes

| Operational Mode | Transport Protocol | Document Context Mechanism | Latency |
| :--- | :--- | :--- | :--- |
| **Mode 2: Text Chat** | Stateless REST HTTP | Injects document context or RAG passages directly into Gemini 3.6 Flash prompt. | ~400ms |
| **Mode 3: Cascaded Voice** | WSS (Deepgram + Flash + Cartesia) | Deepgram transcribes speech $\rightarrow$ Flash 3.6 LLM executes RAG tool $\rightarrow$ Cartesia synthesizes audio. | ~700ms |
| **Mode 4: Native Voice Live** | WSS (Gemini 3.1 Live) | Direct System Context Injection OR `searchUserDocument` function call tool response over WebSockets! | ~300ms |

---

## 4. Document Storage & Lightweight OCR Pipeline

```text
┌──────────────────────────────────┐
│ User Uploads File (PDF/PNG/DOCX) │
└──────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────┐
│ Document Format Router           │
└──────────────────────────────────┘
        │                  │
        ▼ (Text/PDF)       ▼ (Scanned Image/PNG)
┌──────────────┐   ┌────────────────┐
│ Apache Tika  │   │ Tesseract OCR  │ (Lightweight Native OCR Engine)
│ / PDFBox     │   │ Pre-processor  │
└──────────────┘   └────────────────┘
        │                  │
        └─────────┬────────┘
                  │ Extracted Clean Text
                  ▼
┌──────────────────────────────────┐
│ Text Chunking Engine             │ Chunk Size: 512 Tokens, Overlap: 64 Tokens
└──────────────────────────────────┘
                  │
                  ▼
┌──────────────────────────────────┐
│ Vector Embedding Generation      │ Google text-embedding-004 (768 Dimensions)
└──────────────────────────────────┘
                  │
                  ▼
┌──────────────────────────────────┐
│ PostgreSQL pgvector DB           │ Insert into document_embeddings table (HNSW index)
└──────────────────────────────────┘
```

### Storage Locations:
1. **Raw Binaries**: Saved at `/var/reform/storage/documents/{workspaceId}/{documentId}.pdf` or AWS S3.
2. **Metadata**: PostgreSQL `form_documents` table.
3. **Vector Embeddings**: PostgreSQL `document_embeddings` (`pgvector` with HNSW index).
