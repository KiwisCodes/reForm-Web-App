package com.reForm.backend.form.dto;

import com.reForm.backend.form.entity.block.AbstractBlock;

import java.util.List;
import java.util.UUID;

public record FormCreateDto(

        String title,
        UUID workspaceId,
        List<AbstractBlock> blocks
) {
}
