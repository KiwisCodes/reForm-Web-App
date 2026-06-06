package com.reForm.backend.form.dto;

import com.reForm.backend.form.entity.block.BlockType;

public record PublicBlockDto(
        BlockType type,
        String label,
        String description,
        boolean isRequired,
        Integer sortOrder
) {
}
