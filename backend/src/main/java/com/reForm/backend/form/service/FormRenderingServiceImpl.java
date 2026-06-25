package com.reForm.backend.form.service;

import com.reForm.backend.form.dto.PublicFormResponseDto;
import com.reForm.backend.form.entity.Form;
import com.reForm.backend.form.entity.FormStatus;
import com.reForm.backend.form.mapper.FormMapper;
import com.reForm.backend.form.port.IFormRenderingService;
import com.reForm.backend.form.repository.FormRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor

public class FormRenderingServiceImpl implements IFormRenderingService {

    private final FormRepository repository;

    private final FormMapper mapper;

    @Cacheable(value = "forms", key = "#slug")
    @Override
    public PublicFormResponseDto getPublicForm(String slug) {

        Form form = repository.findBySlug(slug).orElseThrow(() -> new IllegalStateException("Form does not exist!"));
        if (form.getStatus() == FormStatus.DRAFT) throw new IllegalStateException("Form is not published!");
        return mapper.toPublicFormResponseDto(form);
    }
}
