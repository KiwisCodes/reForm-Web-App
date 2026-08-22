package com.reForm.backend.ai.service;

import com.reForm.backend.form.entity.Form;
import com.reForm.backend.form.entity.FormStatus;
import com.reForm.backend.form.repository.FormRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

// Extracted out of PublishFormToolHandler (Mode 3/4) so Mode 2's Mode2PublishFormToolHandler can
// call the exact same publish logic without needing a WebSocketSession — see
// backend/knowledge/tdt/week3/mode2_toolcall_architecture.md's "Shared Service Extraction".
// Takes only formId, matching the original handler's actual behavior — it never used
// workspaceId either; workspace membership is already enforced one layer up (FormSecurity /
// @PreAuthorize) before any tool call reaches this service.
@Service
@RequiredArgsConstructor
public class FormPublishService {

    private final FormRepository formRepository;

    public String publishForm(UUID formId) {
        Form form = formRepository.findById(formId)
                .orElseThrow(() -> new IllegalArgumentException("Form not found: " + formId));

        form.setStatus(FormStatus.PUBLISHED);
        formRepository.save(form);

        return form.getSlug() != null
                ? "https://reform.app/f/" + form.getSlug()
                : "https://reform.app/f/" + formId;
    }
}
