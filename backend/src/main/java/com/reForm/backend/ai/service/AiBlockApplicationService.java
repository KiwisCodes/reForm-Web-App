package com.reForm.backend.ai.service;

import com.reForm.backend.ai.dto.AiBlockDto;
import com.reForm.backend.ai.dto.AiFormDto;
import com.reForm.backend.ai.factory.BlockFactory;
import com.reForm.backend.ai.factory.FormFactory;
import com.reForm.backend.form.dto.FormResponseDto;
import com.reForm.backend.form.dto.FormUpdateDto;
import com.reForm.backend.form.entity.block.AbstractBlock;
import com.reForm.backend.form.port.IFormBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

// Applies AI-generated forms/blocks, converting through BlockFactory/FormFactory and delegating
// persistence entirely to the existing IFormBuilderService — no separate repository access here.
// Deliberately depends on form.* (not the other way around), keeping the same one-directional
// package boundary BlockFactory already established.
//
// Only two operations exist: create (no form yet) and update (a form already exists, and the AI's
// returned block list is the complete revised state — appending, editing, and reordering are all
// the same "replace with what the AI returned" operation from this service's point of view; the AI
// is expected to have been given the current blocks as context upstream). Block-level delete is
// intentionally not handled here — deletion is a manual-only UI action per product decision.
@Service
@RequiredArgsConstructor
public class AiBlockApplicationService {

    private final FormFactory formFactory;
    private final BlockFactory blockFactory;
    private final IFormBuilderService formBuilderService;

    public FormResponseDto createFormFromAiBlocks(AiFormDto aiForm, UUID workspaceId, UUID creatorId) {
        return formBuilderService.createForm(formFactory.buildCreateDto(aiForm, workspaceId), creatorId);
    }

    public FormResponseDto updateFormFromAiBlocks(UUID formId, UUID workspaceId, List<AiBlockDto> aiBlocks) {
        List<AbstractBlock> blocks = aiBlocks.stream().map(blockFactory::build).toList();
        return formBuilderService.updateBlocks(new FormUpdateDto(formId, workspaceId, blocks));
    }
}
