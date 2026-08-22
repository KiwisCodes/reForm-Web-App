package com.reForm.backend.ai.service;

import org.springframework.stereotype.Service;

// Extracted out of AnalyzeUploadedFileToolHandler (Mode 3/4) so Mode 2's
// Mode2AnalyzeUploadedFileToolHandler can call the same logic without a WebSocketSession.
// Still a mock — a real Gemini Vision / Apache Tika OCR pipeline is future work.
@Service
public class FileAnalysisService {

    public String analyze(String fileId, String analysisType) {
        return "File " + fileId + " analyzed successfully (" + analysisType + ").";
    }
}
