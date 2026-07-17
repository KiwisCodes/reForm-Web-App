package com.reForm.backend.form.service;

import com.reForm.backend.core.exception.ResourceNotFoundException;
import com.reForm.backend.form.dto.BlockValidationRule;
import com.reForm.backend.form.dto.FormSubmissionDto;
import com.reForm.backend.form.entity.Form;
import com.reForm.backend.form.mapper.FormMapper;
import com.reForm.backend.form.port.IFormQueryPort;
import com.reForm.backend.form.repository.FormRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;


@RequiredArgsConstructor
@Service
public class FormQueryService implements IFormQueryPort {

    private final FormRepository repository;

    private final FormMapper mapper;

    @Override
    public FormSubmissionDto fetchForm(UUID formId) {
        Form form = repository.findById(formId).orElseThrow(() -> new ResourceNotFoundException("Form not found!"));
        List<BlockValidationRule> rules = mapper.toBlockValidationRule(form.getBlocks());
        return mapper.toFormSubmissionDto(form, rules);
    }
}
