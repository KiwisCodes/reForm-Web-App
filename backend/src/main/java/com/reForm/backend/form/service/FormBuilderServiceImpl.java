package com.reForm.backend.form.service;

import com.reForm.backend.core.exception.ResourceNotFoundException;
import com.reForm.backend.form.dto.FormCreateDto;
import com.reForm.backend.form.dto.FormResponseDto;
import com.reForm.backend.form.dto.FormUpdateDto;
import com.reForm.backend.form.entity.Form;
import com.reForm.backend.form.entity.FormStatus;
import com.reForm.backend.form.mapper.FormMapper;
import com.reForm.backend.form.port.IFormBuilderService;
import com.reForm.backend.form.repository.FormRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
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
    public FormResponseDto createForm(FormCreateDto request, UUID creatorId) {
        Form form = new Form();
        form.setTitle(request.title());
        form.setWorkspaceId(request.workspaceId());
        form.setStatus(FormStatus.DRAFT);
        form.setBlocks(request.blocks());
        form.setCreatorId(creatorId);
        repository.save(form);
        return mapper.toResponseDto(form);
    }

    @Override
    public List<FormResponseDto> getAllFormInWorkspace(UUID workspaceId) {
        return mapper.toResponseDtoList(repository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId));
    }

    @Override
    public FormResponseDto retrieveForm(UUID workspaceId, UUID id) {
        return mapper.toResponseDto(repository.findByIdAndWorkspaceId(id, workspaceId).orElseThrow(() -> new ResourceNotFoundException(id + " not found!")));
    }

    @Transactional
    @Override
    @CacheEvict(value = "forms", key = "#result.slug")
    public FormResponseDto updateBlocks(FormUpdateDto request) {
        Form form = repository.findByIdAndWorkspaceId(request.id(), request.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException(request.id() + " not found!"));
        form.setBlocks(request.blocks());
        repository.save(form);
        return mapper.toResponseDto(form);
    }

    @Transactional
    @Override
    @CacheEvict(value = "forms", key = "#result")
    public String delete(UUID id, UUID workspaceId) {
        Form form = repository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException(id + " not found!"));
        String slug = form.getSlug();
        repository.delete(form);
        return slug;
    }

    @Override
    public FormResponseDto publishForm(UUID formId, UUID workspaceId) {
        Form form = repository.findByIdAndWorkspaceId(formId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException(formId + " not found!"));
        form.setStatus(FormStatus.PUBLISHED);
        repository.save(form);
        return mapper.toResponseDto(form);
    }


}
