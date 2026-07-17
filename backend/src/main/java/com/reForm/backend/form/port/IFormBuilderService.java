package com.reForm.backend.form.port;

import com.reForm.backend.form.dto.FormCreateDto;
import com.reForm.backend.form.dto.FormResponseDto;
import com.reForm.backend.form.dto.FormUpdateDto;
import com.reForm.backend.form.entity.Form;
import com.reForm.backend.form.entity.block.AbstractBlock;

import java.util.List;
import java.util.UUID;

public interface IFormBuilderService {

    public FormResponseDto createForm(FormCreateDto request, UUID creatorId);

    public List<FormResponseDto> getAllFormInWorkspace(UUID workspaceId);

    public FormResponseDto retrieveForm(UUID workspaceId, UUID id);

    public FormResponseDto updateBlocks(FormUpdateDto request);

    public String delete(UUID id, UUID workspaceId);

    FormResponseDto publishForm(UUID formId, UUID workspaceId);
}
