package com.reForm.backend.ai.tool.handler.file;

import com.reForm.backend.ai.service.FileAnalysisService;
import com.reForm.backend.ai.tool.port.IToolCallHandler;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * TOOL HANDLER: analyzeUploadedFile
 *
 * WHY THIS TOOL EXISTS:
 * Analyzes uploaded images (via Gemini Vision API) or uploaded documents (via Apache Tika / OCR)
 * so the AI can discuss visual or textual document contents during the conversation.
 *
 * The actual analysis logic lives in FileAnalysisService — shared with Mode 2's
 * Mode2AnalyzeUploadedFileToolHandler, which calls the same service without a WebSocketSession.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeUploadedFileToolHandler implements IToolCallHandler {

    private final FileAnalysisService fileAnalysisService;

    @Override
    public String getFunctionName() {
        return "analyzeUploadedFile";
    }

    @Override
    public Map<String, Object> execute(WebSocketSession clientSession, JsonNode functionCall, String callId) {
        JsonNode args = functionCall.path("args");
        String fileId = args.path("fileId").asText();
        String analysisType = args.path("analysisType").asText("DESCRIBE");

        log.info("🔍 [ANALYZE UPLOADED FILE HANDLER] FileId: {}, AnalysisType: {}", fileId, analysisType);

        String analysis = fileAnalysisService.analyze(fileId, analysisType);

        return Map.of(
            "id", callId,
            "name", getFunctionName(),
            "response", Map.of("result", Map.of(
                "status", "ANALYZED",
                "fileId", fileId,
                "analysis", analysis
            ))
        );
    }
}
