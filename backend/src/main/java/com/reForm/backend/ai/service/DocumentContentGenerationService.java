package com.reForm.backend.ai.service;

import org.springframework.stereotype.Service;

// Extracted out of GenerateContentFromDocToolHandler (Mode 3/4) so Mode 2's
// Mode2GenerateContentFromDocumentToolHandler can call the same logic without a WebSocketSession.
// Still a mock — a real document-grounded generation pipeline is future work.
@Service
public class DocumentContentGenerationService {

    public String generateContent(String fileId, String contentType, int count) {
        return "Generated " + count + " " + contentType + " from document " + fileId;
    }
}
