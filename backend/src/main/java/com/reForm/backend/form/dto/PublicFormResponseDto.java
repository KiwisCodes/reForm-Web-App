package com.reForm.backend.form.dto;

import java.util.List;

public record PublicFormResponseDto(
        String title,
        String slug,
        List<PublicBlockDto> blocks
) {
}
