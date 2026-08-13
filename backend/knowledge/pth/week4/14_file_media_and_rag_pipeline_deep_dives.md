# 14: File, Media & RAG Pipeline Deep Dives

**Author**: Lead Technical Writer & Architect  
**Platform**: reForm Enterprise Form Builder & Conversational AI Platform (`com.reForm.backend.ai`)  
**Target Document**: `backend/knowledge/pth/week4/14_file_media_and_rag_pipeline_deep_dives.md`  
**Date**: 2026-08-13  
**Version**: 2.0.0-RELEASE  

---

### Group D: File & Media Processing Pipeline (P1–P3)
*Agents for handling uploads, OCR, STT, and document indexing.*

#### 23. `RequestFileUploadToolHandler`
1. **Name & Role**: `RequestFileUploadToolHandler` pushes interactive WebSocket UI frames prompting the user browser to display a file drag-and-drop zone.
2. **Trigger Mechanism**: `ToolCallRegistry.executeTool()` handling Gemini `requestFileUpload` function call frame.
3. **Input / Output**:
   - **Input**: `WebSocketSession clientSession`, Jackson `JsonNode functionCall`, `String callId`. (Extracted args: `label`, `acceptedTypes`).
   - **Output**: `Map<String, Object>` Gemini `toolResponse` payload (`"status": "UPLOAD_ZONE_RENDERED"`). Client frame pushed: `{"type": "FILE_UPLOAD_REQUESTED", ...}`.
4. **Design Pattern**: **Strategy Pattern** (`IToolCallHandler`) + **Observer / Client Push Pattern**. *WHY*: Bridges backend AI decisions with real-time browser UI widget rendering.
5. **Technology Choice**: **Spring WebSocket Session (`WebSocketSessionUtils.wrapSafeSession`)** over HTTP long-polling. *WHY*: Delivers sub-10ms UI widget render triggers directly over existing WebSocket connections.
6. **SOLID + KISS Justification**:
   - *SRP*: Dedicated to triggering file upload UI rendering on the client browser.
   - *OCP*: Extensible to support custom file size limit parameters cleanly.
   - *LSP*: Satisfies `IToolCallHandler` contract.
   - *ISP*: Minimal strategy interface methods.
   - *DIP*: Depends on `WebSocketSession` abstraction.
   - *KISS*: Sends a JSON text frame with type `FILE_UPLOAD_REQUESTED`.
7. **Open Questions**: Should requesting a file upload automatically pause voice stream audio output until the file upload completes?


---

#### 24. `AnalyzeUploadedFileToolHandler`
1. **Name & Role**: `AnalyzeUploadedFileToolHandler` invokes multimodal vision models or text analysis tools on user-uploaded file attachments.
2. **Trigger Mechanism**: `ToolCallRegistry.executeTool()` handling Gemini `analyzeUploadedFile` function call frame.
3. **Input / Output**:
   - **Input**: `WebSocketSession clientSession`, Jackson `JsonNode functionCall`, `String callId`. (Extracted args: `fileId`, `analysisType`, `question`).
   - **Output**: `Map<String, Object>` Gemini `toolResponse` payload (`"status": "ANALYZED", "analysis": "..."`).
4. **Design Pattern**: **Strategy Pattern** (`IToolCallHandler`) + **Adapter Pattern**. *WHY*: Encapsulates multimodal file analysis tool execution.
5. **Technology Choice**: **Gemini 3.6 Flash Vision API + Apache Tika** over external vision microservices. *WHY*: Gemini native vision capabilities extract insights directly from image files and charts.
6. **SOLID + KISS Justification**:
   - *SRP*: Focuses purely on analyzing uploaded file content.
   - *OCP*: Extensible to support new analysis types (`SUMMARIZE`, `DATA_EXTRACT`) via ENUMs.
   - *LSP*: Fulfills `IToolCallHandler` contract.
   - *ISP*: Minimal tool strategy dependencies.
   - *DIP*: Depends on `IFileAnalysisService` interface.
   - *KISS*: Returns analysis result string directly in tool response map.
7. **Open Questions**: How should multi-page image files (PDFs converted to PNGs) be batched when sending to vision model APIs?


---

#### 25. `ExtractStructuredDataToolHandler`
1. **Name & Role**: `ExtractStructuredDataToolHandler` extracts structured key-value field pairs (e.g. full name, email, work history) from uploaded resumes and documents.
2. **Trigger Mechanism**: `ToolCallRegistry.executeTool()` handling Gemini `extractStructuredData` function call frame.
3. **Input / Output**:
   - **Input**: `WebSocketSession clientSession`, Jackson `JsonNode functionCall`, `String callId`. (Extracted args: `fileId`, `fieldsToExtract`).
   - **Output**: `Map<String, Object>` Gemini `toolResponse` payload (`"status": "EXTRACTED", "extractedFields": "fullName,email"`).
4. **Design Pattern**: **Strategy Pattern** (`IToolCallHandler`). *WHY*: Encapsulates structured data extraction logic for file tool calls.
5. **Technology Choice**: **Gemini 3.6 Flash (Mode 2) Structured JSON Output** over regex parsers. *WHY*: LLM JSON mode handles unstructured resume variations far better than static regular expressions.
6. **SOLID + KISS Justification**:
   - *SRP*: Sole responsibility of extracting key-value field pairs from files.
   - *OCP*: Extraction target field schemas extend dynamically per form requirements.
   - *LSP*: Implements `IToolCallHandler` contract.
   - *ISP*: Concise interface methods.
   - *DIP*: Depends on `IStructuredDataExtractor` abstraction.
   - *KISS*: Returns extracted fields as a flat JSON key-value map.
7. **Open Questions**: How to handle confidence scores when extracted fields (e.g. phone number) are ambiguous in the source document?


---

#### 26. `SaveSessionTranscriptToolHandler`
1. **Name & Role**: `SaveSessionTranscriptToolHandler` persists full timestamped conversation dialogue arrays to PostgreSQL for audit logging and analytics.
2. **Trigger Mechanism**: `ToolCallRegistry.executeTool()` handling Gemini `saveSessionTranscript` function call frame.
3. **Input / Output**:
   - **Input**: `WebSocketSession clientSession`, Jackson `JsonNode functionCall`, `String callId`. (Extracted args: `includeTimestamps`, `includeEvaluation`).
   - **Output**: `Map<String, Object>` Gemini `toolResponse` payload (`"status": "TRANSCRIPT_SAVED"`).
4. **Design Pattern**: **Strategy Pattern** (`IToolCallHandler`) + **Repository Pattern**. *WHY*: Encapsulates dialogue transcript persistence.
5. **Technology Choice**: **PostgreSQL JSONB (`Submission.transcript`)** over text log files. *WHY*: JSONB column indexing enables fast structured querying of dialogue turns.
6. **SOLID + KISS Justification**:
   - *SRP*: Dedicated purely to transcript formatting and persistence.
   - *OCP*: Extensible to format transcripts as PDF or Markdown without changing tool logic.
   - *LSP*: Fulfills `IToolCallHandler` contract.
   - *ISP*: Minimal interface contract.
   - *DIP*: Depends on `SubmissionRepository` abstraction.
   - *KISS*: Writes transcript JSON array directly to database row.
7. **Open Questions**: Should PII redactor filters automatically sanitize transcript text before database write operations?

---

### 4.4 Background Pipeline Agents

#### 27. `SaveAudioRecordingToolHandler`
1. **Name & Role**: `SaveAudioRecordingToolHandler` compresses and persists raw session PCM audio streams to AWS S3 storage for audit compliance and grading.
2. **Trigger Mechanism**: `ToolCallRegistry.executeTool()` handling Gemini `saveAudioRecording` function call frame.
3. **Input / Output**:
   - **Input**: `WebSocketSession clientSession`, Jackson `JsonNode functionCall`, `String callId`. (Extracted args: `scope`, `label`, `retentionDays`).
   - **Output**: `Map<String, Object>` Gemini `toolResponse` payload (`"status": "RECORDING_SAVED", "retentionDays": 90`).
4. **Design Pattern**: **Strategy Pattern** (`IToolCallHandler`) + **Adapter Pattern** (S3 storage adapter). *WHY*: Encapsulates audio archival side-effects.
5. **Technology Choice**: **FFmpeg / Opus Audio Compression + AWS S3 SDK v2** over raw WAV storage. *WHY*: Opus compression reduces audio storage footprint by 85% while maintaining voice clarity.
6. **SOLID + KISS Justification**:
   - *SRP*: Sole focus of compressing and persisting session audio streams.
   - *OCP*: Pluggable storage providers (S3, GCP Cloud Storage, Azure Blob) via `IAudioStorageAdapter`.
   - *LSP*: Fulfills `IToolCallHandler` contract.
   - *ISP*: Minimal interface dependency.
   - *DIP*: Depends on `IAudioStorageAdapter` interface.
   - *KISS*: Stores compressed Opus files under S3 key `audio/{sessionId}.opus`.
7. **Open Questions**: How should audio encryption keys be rotated for enterprise tenants with strict HIPAA/SOC2 requirements?


---

#### 28. `DocumentChunkingEmbeddingAgent`
1. **Name & Role**: `DocumentChunkingEmbeddingAgent` parses document text into semantic chunks, generates vector embeddings, and indexes them in PostgreSQL `pgvector` HNSW indexes for RAG search.
2. **Trigger Mechanism**: Spring Event Bus consuming `com.reForm.backend.ai.event.DocumentIngestionEvent`.
3. **Input / Output**:
   - **Input**: `com.reForm.backend.ai.event.DocumentIngestionEvent`
     ```java
     public record DocumentIngestionEvent(
         UUID documentId,
         UUID workspaceId,
         String extractedText,
         Map<String, String> metadata
     ) {}
     ```
   - **Output**: `com.reForm.backend.ai.event.DocumentEmbeddingResult`
     ```java
     public record DocumentEmbeddingResult(
         UUID documentId,
         int totalChunksCreated,
         int embeddingsStored,
         String vectorIndexName,
         long processingTimeMs
     ) {}
     ```
4. **Design Pattern**: **Pipeline Pattern** (Text Extraction $\rightarrow$ Semantic Chunking $\rightarrow$ Embedding Generation $\rightarrow$ Vector Storage) combined with **Strategy Pattern** (chunking algorithms). *WHY*: Decouples text chunking logic from vector embedding API invocation.
5. **Technology Choice**: **Google `text-embedding-004` (768d) + PostgreSQL `pgvector` (HNSW index `vector_cosine_ops`)** over Pinecone/Weaviate. *WHY*: Storing vectors inside PostgreSQL eliminates multi-database sync overhead and maintains ACID compliance.
6. **SOLID + KISS Justification**:
   - *SRP*: Handles semantic text chunking, embedding generation, and vector database indexing.
   - *OCP*: Chunking strategies (`IChunkingStrategy`) extend for tabular data, code, or markdown without touching indexers.
   - *LSP*: Substitutable `IEmbeddingModel` abstraction (Gemini, OpenAI, Cohere).
   - *ISP*: Exposes clean `IEmbeddingIndexer` interface.
   - *DIP*: Depends on `IVectorRepository` and `IEmbeddingModel` abstractions.
   - *KISS*: Uses standard 768-dimensional float arrays stored in native PostgreSQL `vector` columns.
7. **Open Questions**: What is the optimal chunk size (e.g. 512 tokens with 64-token overlap) to maximize recall precision during real-time voice RAG queries?
8. **Spring AI Integration Analysis**:
   - **Problem (Without Spring AI)**: Writing custom text splitters, manual `text-embedding-004` API calls, and raw JDBC `pgvector` insert SQL.
   - **Spring AI Solution**: Use `TokenTextSplitter` for semantic chunking and `PgVectorStore` (`spring-ai-starter-vector-store-pgvector`) for auto-indexing embeddings into PostgreSQL `vector_store`.
   - **Benefit vs. Current Design**: Replaces ~200 lines of custom chunking, embedding HTTP calls, and SQL insert queries with standard Spring AI ETL pipeline abstractions (`Reader` $\rightarrow$ `TokenTextSplitter` $\rightarrow$ `PgVectorStore`).
   - **Migration Impact**: MEDIUM — Requires aligning table schemas with `PgVectorStore` auto-configuration.
   - **Recommendation**: **USE** — Major reduction in vector DB boilerplate and embedding management.


---

#### 29. `MalwareScanAgent`
1. **Name & Role**: `MalwareScanAgent` inspects uploaded files for binary malware signatures, suspicious embedded scripts, and container vulnerabilities before passing files to OCR or RAG indexing pipelines.
2. **Trigger Mechanism**: Spring Event Bus consuming `com.reForm.backend.ai.event.DocumentUploadedEvent`.
3. **Input / Output**:
   - **Input**: `com.reForm.backend.ai.event.DocumentUploadedEvent`
     ```java
     public record DocumentUploadedEvent(
         UUID documentId,
         UUID workspaceId,
         String fileKey,
         String contentType,
         long fileSizeBytes,
         byte[] headerBytes
     ) {}
     ```
   - **Output**: `com.reForm.backend.ai.event.MalwareScanResult`
     ```java
     public record MalwareScanResult(
         UUID documentId,
         ScanStatus status,
         String virusName,
         String fileHashSha256,
         boolean safeForProcessing
     ) {}
     ```
4. **Design Pattern**: **Chain of Responsibility** (magic header check $\rightarrow$ signature scan $\rightarrow$ sandbox heuristic scan) combined with **Observer Pattern**. *WHY*: Fast-fails known malicious files at step 1 before incurring heavy processing overhead.
5. **Technology Choice**: **ClamAV REST Container + Apache Tika (MIME spoofing detection)** over simple extension checks. *WHY*: ClamAV provides enterprise open-source virus scanning while Tika detects file extension spoofing attacks.
6. **SOLID + KISS Justification**:
   - *SRP*: Exclusively handles file safety, virus detection, and binary integrity validation.
   - *OCP*: Additional scan engines (VirusTotal API) append to scan chain seamlessly.
   - *LSP*: All scan stages implement `IScanStage`.
   - *ISP*: Clean interface `IMalwareScanner`.
   - *DIP*: Orchestration depends on high-level `IScanStage` interface.
   - *KISS*: Fast-fails immediately if MIME headers contradict actual magic bytes.
7. **Open Questions**: Should infected files be hard-deleted immediately or retained in an isolated quarantine S3 bucket for forensic analysis?


---

#### 30. `DocumentOcrSubAgent`
1. **Name & Role**: `DocumentOcrSubAgent` extracts clean text from scanned images and PDF attachments using optical character recognition engines.
2. **Trigger Mechanism**: `SubAgentFactory.spawnDocumentOcrSubAgent()` processing file upload events.
3. **Input / Output**:
   - **Input**: Image/PDF byte array, document mime type, language hints.
   - **Output**: Extracted raw text string, bounding box metadata, OCR confidence score.
4. **Design Pattern**: **Factory Pattern** (`SubAgentFactory`) + **Adapter Pattern** (abstracting OCR engine instances). *WHY*: Isolates native image processing libraries from primary application server threads.
5. **Technology Choice**: **Apache Tika + Tesseract OCR 5.0 (Native C++ bindings)** over cloud OCR APIs. *WHY*: Eliminates third-party per-page API costs and reduces network latency for standard document scans.
6. **SOLID + KISS Justification**:
   - *SRP*: Single focus of extracting raw text from binary document images.
   - *OCP*: New OCR engines (AWS Textract, Google Vision) register via `IOcrEngine` adapters.
   - *LSP*: Fulfills `ISubAgentWorker` contract.
   - *ISP*: Exposes clean `extractText()` interface.
   - *DIP*: Depends on `IOcrEngine` abstraction.
   - *KISS*: Returns clean plain text string strips of control characters.
7. **Open Questions**: How should multi-column layout scanned PDFs be parsed to maintain correct reading order during text extraction?
8. **Spring AI Integration Analysis**:
   - **Problem (Without Spring AI)**: Direct dependency on raw Apache Tika and Tesseract C++ JNI calls.
   - **Spring AI Solution**: Use `TikaDocumentReader` from `spring-ai-core`.
   - **Benefit vs. Current Design**: Provides standardized `Document` abstraction for extracted text and metadata across all PDF, DOCX, and scanned image types.
   - **Migration Impact**: LOW — Replaces manual Tika initialization code inside sub-agent.
   - **Recommendation**: **USE** — Standardizes document text extraction across the platform.


---

#### 31. `AudioTranscriptionSubAgent`
1. **Name & Role**: `AudioTranscriptionSubAgent` performs high-accuracy asynchronous speech-to-text transcription, speaker diarization, and sentiment tagging on recorded voice sessions.
2. **Trigger Mechanism**: Spring Event Bus consuming `com.reForm.backend.ai.event.AudioTranscriptionRequestEvent`.
3. **Input / Output**:
   - **Input**: `com.reForm.backend.ai.event.AudioTranscriptionRequestEvent`
     ```java
     public record AudioTranscriptionRequestEvent(
         UUID audioId,
         UUID sessionId,
         String audioFileUrl,
         String audioFormat,
         String targetLanguage
     ) {}
     ```
   - **Output**: `com.reForm.backend.ai.event.AudioTranscriptionResult`
     ```java
     public record AudioTranscriptionResult(
         UUID audioId,
         String fullTranscriptText,
         List<TranscriptSegment> segments,
         String dominantSentiment,
         double wordConfidenceScore
     ) {}
     ```
4. **Design Pattern**: **Worker Thread / Task Queue Pattern** (offloading heavy audio processing) combined with **Adapter Pattern** (STT provider abstraction). *WHY*: Long audio files take seconds/minutes to transcribe and must execute asynchronously.
5. **Technology Choice**: **Deepgram Nova-3 / Faster-Whisper + Spring AMQP** over synchronous REST API calls. *WHY*: Message queues prevent HTTP timeouts and support exponential retry backoffs for long audio jobs.
6. **SOLID + KISS Justification**:
   - *SRP*: Dedicated to audio-to-text conversion, speaker diarization, and confidence scoring.
   - *OCP*: Pluggable `ISpeechToTextProvider` supports switching between Deepgram, Whisper, and Google STT.
   - *LSP*: All STT adapters return unified `AudioTranscriptionResult` records.
   - *ISP*: Segregated `ITranscriptionEngine` and `IDiarizationEngine` interfaces.
   - *DIP*: Depends on `ISpeechToTextProvider` abstraction.
   - *KISS*: Emits transcript segments with explicit start/end timestamps in milliseconds.
7. **Open Questions**: For multi-speaker interviews, how can speaker diarization accurately map audio segments to candidate vs interviewer dialogue?


---

### Group E: Knowledge Retrieval & RAG Pipeline (P1)
*Agents powering semantic search and factual knowledge retrieval.*

#### 32. `SearchUserDocumentToolHandler`
1. **Name & Role**: `SearchUserDocumentToolHandler` executes semantic vector RAG search queries over user documents during active voice/text sessions.
2. **Trigger Mechanism**: `ToolCallRegistry.executeTool()` handling Gemini `searchUserDocument` function call frame.
3. **Input / Output**:
   - **Input**: `WebSocketSession clientSession`, Jackson `JsonNode functionCall`, `String callId`. (Extracted args: `query`).
   - **Output**: `Map<String, Object>` Gemini `toolResponse` payload (`"status": "SUCCESS", "content": "Document context..."`).
4. **Design Pattern**: **Strategy Pattern** (`IToolCallHandler`). *WHY*: Encapsulates tool-driven vector retrieval.
5. **Technology Choice**: **`RagSearchAgent` Delegation + `pgvector`** over inline database queries. *WHY*: Delegates retrieval logic to dedicated RAG agent bean.
6. **SOLID + KISS Justification**:
   - *SRP*: Responsible only for extracting query arguments and executing RAG retrieval.
   - *OCP*: Extensible to support filtering by document ID without altering tool handler contract.
   - *LSP*: Implements `IToolCallHandler`.
   - *ISP*: Minimal interface dependency.
   - *DIP*: Depends on `RagSearchAgent` abstraction.
   - *KISS*: Calls `ragSearchAgent.search(query)` and wraps result in tool response map.
7. **Open Questions**: How should search results be formatted if no relevant passages exceed the minimum similarity score?
8. **Spring AI Integration Analysis**:
   - **Problem (Without Spring AI)**: Manual vector query parameter preparation and custom result mapping.
   - **Spring AI Solution**: Delegate query execution to `PgVectorStore.similaritySearch(SearchRequest.query(q).withTopK(3))`.
   - **Benefit vs. Current Design**: Clean $O(1)$ vector similarity lookup using Spring AI's unified `VectorStore` port.
   - **Migration Impact**: LOW — Operates inside existing tool handler strategy method.
   - **Recommendation**: **USE** — Clean vector store query abstraction.


---

#### 33. `RagSearchAgent`
1. **Name & Role**: `RagSearchAgent` retrieves factual knowledge from uploaded company documents, handbooks, and rubrics during active filler sessions.
2. **Trigger Mechanism**: Executed when Gemini emits `searchUserDocument` function tool call.
3. **Input / Output**:
   - **Input**: Search query string, topK count (default: 3).
   - **Output**: Top-K relevant document text passages with similarity scores.
4. **Design Pattern**: **Repository / Strategy Pattern**. *WHY*: Encapsulates vector similarity search and hybrid full-text retrieval logic.
5. **Technology Choice**: **Google `text-embedding-004` (768d) + PostgreSQL `pgvector` HNSW Index (`vector_cosine_ops`) + tsvector Hybrid Search** over pure vector search. *WHY*: Hybrid search combines semantic embedding recall with exact keyword string matching for high precision.
6. **SOLID + KISS Justification**:
   - *SRP*: Dedicated purely to knowledge retrieval and document chunk ranking.
   - *OCP*: Search strategies (vector-only, keyword-only, hybrid) swap via configuration.
   - *LSP*: Implements `IRagSearchEngine` contract.
   - *ISP*: Concise interface methods.
   - *DIP*: Depends on `pgvector` repository abstraction.
   - *KISS*: Returns top-3 text snippets concatenated into a single string for tool response payload.
7. **Open Questions**: How to balance vector similarity scoring when user documents contain conflicting information across different uploaded file versions?
8. **Spring AI Integration Analysis**:
   - **Problem (Without Spring AI)**: Custom embedding generation using `text-embedding-004` and custom `pgvector` SQL cosine distance (`<=>`) query execution.
   - **Spring AI Solution**: Use `PgVectorStore` paired with `QuestionAnswerAdvisor` or direct `similaritySearch` with filter expressions.
   - **Benefit vs. Current Design**: Replaces manual SQL queries and vector distance calculations with declarative Spring AI `VectorStore` queries and built-in RAG advisors.
   - **Migration Impact**: MEDIUM — Aligns RAG search engine bean with `PgVectorStore`.
   - **Recommendation**: **USE** — Eliminates custom vector search boilerplate.
