package com.reForm.backend.form.service;

import com.reForm.backend.form.dto.FormCreateDto;
import com.reForm.backend.form.dto.FormResponseDto;
import com.reForm.backend.form.dto.FormUpdateDto;
import com.reForm.backend.form.entity.Form;
import com.reForm.backend.form.entity.FormStatus;
import com.reForm.backend.form.entity.block.AbstractBlock;
import com.reForm.backend.form.mapper.FormMapper;
import com.reForm.backend.form.port.IFormBuilderService;
import com.reForm.backend.form.repository.FormRepository;
import jakarta.transaction.Transactional;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor

public class FormBuilderServiceImpl implements IFormBuilderService {

    private final FormMapper mapper;

    private final FormRepository repository;

    @Override
    @Transactional
    public FormResponseDto createForm(FormCreateDto request) {
        Form form = new Form();
        form.setTitle(request.title());
        form.setWorkspaceId(request.workspaceId());
        form.setStatus(FormStatus.DRAFT);
        repository.save(form);
        return mapper.toResponseDto(form);
    }

    @Override
    public List<FormResponseDto> getAllFormInWorkspace(UUID workspaceId) {
        return mapper.toResponseDtoList(repository.findByWorkspaceIdOrderByCreatedDateDesc(workspaceId));
    }

    @Override
    public FormResponseDto retrieveForm(UUID workspaceId, UUID id) {
        return mapper.toResponseDto(repository.findByIdAndWorkspaceId(workspaceId, id).orElseThrow(() -> new IllegalStateException(id + "not found!")));
    }

    @Transactional
    @Override
    public FormResponseDto updateBlocks(FormUpdateDto request) {
        Form form = repository.findByIdAndWorkspaceId(request.id(), request.workspaceId())
                .orElseThrow(() -> new IllegalStateException(request.id() + "not found!"));
        form.setBlocks(request.blocks());
        repository.save(form);
        return mapper.toResponseDto(form);
    }

    @Override
    public void delete(UUID id, UUID workspaceId) {
        Form form = repository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new IllegalStateException(id + "not found!"));
        repository.delete(form);
    }

}
