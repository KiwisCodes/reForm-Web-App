package com.reForm.backend.form.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.reForm.backend.form.entity.Form;
import com.reForm.backend.form.entity.FormStatus;
import com.reForm.backend.form.entity.block.AbstractBlock;
import com.reForm.backend.form.port.IFormBuilderService;
import com.reForm.backend.form.repository.FormRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FormBuilderServiceImpl implements IFormBuilderService{

    private final FormRepository formRepository;

    @Override
    public Form createForm(String title, UUID workspaceId) {
        Form form = new Form();
        form.setTitle(title);
        form.setWorkspaceId(workspaceId);
        form.setStatus(FormStatus.DRAFT);
        formRepository.save(form);
        return form;
    }

    @Override
    public Form retrieveForm(UUID id, UUID workspaceId) {
        return formRepository.findByIdAndWorkspaceId(id, workspaceId).orElseThrow(() -> new RuntimeException("Cannot find the form"));
    }

    @Override
    public List<Form> listAllForms(UUID workspaceId) {
        return formRepository.findByWorkspaceIdOrderByCreatedDateDesc(workspaceId);
    }

    @Transactional
    @Override
    public Form updateBlocks(UUID id, UUID workspaceId, List<AbstractBlock> blocks) {
        Form form = retrieveForm(id, workspaceId);
        form.setBlocks(blocks);
        formRepository.save(form);
        return form;
    }

    @Override
    public void deleteForm(UUID id, UUID workspaceId) {
        Form form = retrieveForm(id, workspaceId);
        formRepository.delete(form);
    }
}