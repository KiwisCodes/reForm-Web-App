package com.reForm.backend.ai.factory;

import com.reForm.backend.ai.dto.AiFormDto;
import com.reForm.backend.form.dto.FormCreateDto;
import com.reForm.backend.form.entity.block.AbstractBlock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

// Mirrors BlockFactory one level up: converts the AI's whole-form response into a FormCreateDto,
// reusing BlockFactory for each block. workspaceId is an explicit parameter, never read from
// AiFormDto — it comes from the authenticated caller's request context (same boundary
// BuilderController already enforces for the manual path), not from AI-generated content.
@Component
@RequiredArgsConstructor
public class FormFactory {

    private final BlockFactory blockFactory;

    public FormCreateDto buildCreateDto(AiFormDto aiForm, UUID workspaceId) {
        List<AbstractBlock> blocks = aiForm.blocks().stream().map(blockFactory::build).toList();
        return new FormCreateDto(aiForm.title(), workspaceId, blocks);
    }
}
