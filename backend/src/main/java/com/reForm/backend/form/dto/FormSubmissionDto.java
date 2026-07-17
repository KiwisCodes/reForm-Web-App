package com.reForm.backend.form.dto;

import com.reForm.backend.form.entity.FormStatus;

import java.util.List;

public record FormSubmissionDto(
        FormStatus status,
        List<BlockValidationRule> rules
) {
}
