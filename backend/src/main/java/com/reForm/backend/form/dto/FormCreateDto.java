package com.reForm.backend.form.dto;

import java.util.UUID;

public record FormCreateDto(

        String title,
        UUID workspaceId
) {
}
