package com.reForm.backend.ai.event;

import java.util.UUID;

/**
 * DOCUMENT INGESTION EVENT
 * 
 * Published when a user uploads documents for schema generation or form context background ingestion.
 */
public record DocumentIngestionEvent(
    UUID documentId,
    byte[] content,
    String mimeType
) {}
