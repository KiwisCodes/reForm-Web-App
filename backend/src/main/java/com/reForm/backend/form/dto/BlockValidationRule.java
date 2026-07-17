package com.reForm.backend.form.dto;

import java.util.UUID;

public record BlockValidationRule(
        UUID id,
        boolean isRequired
) {
}
