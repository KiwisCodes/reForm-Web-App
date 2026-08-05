package com.reForm.backend.ai.event;

import java.util.UUID;

/**
 * RAG QUERY EVENT
 * 
 * Published to execute asynchronous vector similarity search queries against form document embeddings.
 */
public record RagQueryEvent(
    UUID formId,
    String queryText,
    int topK
) {}
