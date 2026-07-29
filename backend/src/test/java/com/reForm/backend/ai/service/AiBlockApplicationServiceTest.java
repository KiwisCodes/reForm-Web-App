package com.reForm.backend.ai.service;

import com.reForm.backend.ai.dto.AiBlockDto;
import com.reForm.backend.ai.dto.AiFormDto;
import com.reForm.backend.ai.dto.AiStaticBlockDto;
import com.reForm.backend.ai.factory.BlockFactory;
import com.reForm.backend.ai.factory.FormFactory;
import com.reForm.backend.form.dto.FormCreateDto;
import com.reForm.backend.form.dto.FormResponseDto;
import com.reForm.backend.form.dto.FormUpdateDto;
import com.reForm.backend.form.entity.block.AbstractBlock;
import com.reForm.backend.form.entity.block.staticblock.standard.ShortTextStaticBlock;
import com.reForm.backend.form.port.IFormBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiBlockApplicationServiceTest {

    @Mock
    private FormFactory formFactory;

    @Mock
    private BlockFactory blockFactory;

    @Mock
    private IFormBuilderService formBuilderService;

    @Test
    void createFormFromAiBlocksDelegatesToFormFactoryThenCreateForm() {
        AiBlockApplicationService service = new AiBlockApplicationService(formFactory, blockFactory, formBuilderService);

        AiFormDto aiForm = new AiFormDto("Feedback Form", List.of(new AiStaticBlockDto()));
        UUID workspaceId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();

        FormCreateDto builtCreateDto = new FormCreateDto("Feedback Form", workspaceId, List.of(new ShortTextStaticBlock()));
        when(formFactory.buildCreateDto(aiForm, workspaceId)).thenReturn(builtCreateDto);

        FormResponseDto expectedResponse = new FormResponseDto(UUID.randomUUID(), "Feedback Form", null, List.of(), null, "feedback-form");
        when(formBuilderService.createForm(builtCreateDto, creatorId)).thenReturn(expectedResponse);

        FormResponseDto result = service.createFormFromAiBlocks(aiForm, workspaceId, creatorId);

        assertSame(expectedResponse, result);
    }

    @Test
    void updateFormFromAiBlocksConvertsEachDtoAndDelegatesToUpdateBlocks() {
        AiBlockApplicationService service = new AiBlockApplicationService(formFactory, blockFactory, formBuilderService);

        AiBlockDto dto = new AiStaticBlockDto();
        AbstractBlock builtBlock = new ShortTextStaticBlock();
        when(blockFactory.build(dto)).thenReturn(builtBlock);

        UUID formId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        FormResponseDto expectedResponse = new FormResponseDto(UUID.randomUUID(), "Feedback Form", null, List.of(), null, "feedback-form");
        ArgumentCaptor<FormUpdateDto> captor = ArgumentCaptor.forClass(FormUpdateDto.class);
        when(formBuilderService.updateBlocks(captor.capture())).thenReturn(expectedResponse);

        FormResponseDto result = service.updateFormFromAiBlocks(formId, workspaceId, List.of(dto));

        assertSame(expectedResponse, result);
        assertEquals(formId, captor.getValue().id());
        assertEquals(workspaceId, captor.getValue().workspaceId());
        assertEquals(List.of(builtBlock), captor.getValue().blocks());
    }
}
