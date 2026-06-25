package com.reForm.backend.form.dto;

import com.reForm.backend.form.entity.FormStatus;
import com.reForm.backend.form.entity.block.AbstractBlock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record FormResponseDto(
        UUID id,
        String title,
        FormStatus status,
        List<AbstractBlock> blocks,
        LocalDateTime createdAt,
        String slug
) {
}
