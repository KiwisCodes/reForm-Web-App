package com.reForm.backend.ai.tool.handler.builder;

import com.reForm.backend.ai.tool.port.IToolCallHandler;
import com.reForm.backend.form.entity.Form;
import com.reForm.backend.form.entity.FormStatus;
import com.reForm.backend.form.repository.FormRepository;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * TOOL HANDLER: publishForm
 * 
 * WHY THIS TOOL EXISTS:
 * When a Form Builder finishes designing their form conversationally ("I'm done, publish this form"),
 * this tool updates the form's status to PUBLISHED in PostgreSQL and returns the shareable public URL.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublishFormToolHandler implements IToolCallHandler {

    private final FormRepository formRepository;

    @Override
    public String getFunctionName() {
        return "publishForm";
    }

    @Override
    public Map<String, Object> execute(WebSocketSession clientSession, JsonNode functionCall, String callId) {
        String formId = (String) clientSession.getAttributes().get("formId");
        String visibility = functionCall.path("args").path("visibility").asText("PUBLIC");

        log.info("🚀 [PUBLISH FORM HANDLER] Publishing FormId: {}, Visibility: {}", formId, visibility);

        String publicUrl = "https://reform.app/f/" + formId;

        if (formId != null && !formId.isBlank()) {
            try {
                UUID formUuid = UUID.fromString(formId);
                Optional<Form> formOpt = formRepository.findById(formUuid);
                if (formOpt.isPresent()) {
                    Form form = formOpt.get();
                    form.setStatus(FormStatus.PUBLISHED);
                    formRepository.save(form);
                    if (form.getSlug() != null) {
                        publicUrl = "https://reform.app/f/" + form.getSlug();
                    }
                    log.info("✅ Form status updated to PUBLISHED for FormId: {}", formId);
                }
            } catch (Exception e) {
                log.error("Error publishing form for FormId: {}", formId, e);
            }
        }

        return Map.of(
            "id", callId,
            "name", getFunctionName(),
            "response", Map.of("result", Map.of(
                "status", "SUCCESS",
                "formStatus", "PUBLISHED",
                "publicUrl", publicUrl
            ))
        );
    }
}
