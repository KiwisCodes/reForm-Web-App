package com.reForm.backend.ai.dto;

import com.reForm.backend.form.dto.FormResponseDto;

// HTTP-facing response for a completed Mode 2 chat turn: the form's full state after Gemini's
// schema-constrained blocks were parsed and applied. No separate natural-language assistant reply
// field exists in v1 — Mode 2 skips streaming/conversational text for now (see the week3/mode2
// mental model doc's streaming sub-problem), so the model's entire output is already folded into
// `form` by the time this DTO is built.
public record ChatResponseDto(
        FormResponseDto form
) {
}
