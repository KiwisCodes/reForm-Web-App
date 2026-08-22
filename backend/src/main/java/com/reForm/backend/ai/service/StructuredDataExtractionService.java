package com.reForm.backend.ai.service;

import org.springframework.stereotype.Service;

// Extracted out of ExtractStructuredDataToolHandler (Mode 3/4) so Mode 2's
// Mode2ExtractStructuredDataToolHandler can call the same logic without a WebSocketSession.
// Still a mock — a real key-value extraction pipeline is future work.
@Service
public class StructuredDataExtractionService {

    public String extract(String fileId, String fieldsToExtract) {
        return fieldsToExtract;
    }
}
