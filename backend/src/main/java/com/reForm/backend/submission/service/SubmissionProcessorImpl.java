package com.reForm.backend.submission.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.reForm.backend.form.entity.Form;
import com.reForm.backend.form.entity.FormStatus;
import com.reForm.backend.form.entity.block.AbstractBlock;
import com.reForm.backend.form.mapper.FormMapper;
import com.reForm.backend.form.port.IFormBuilderService;
import com.reForm.backend.form.repository.FormRepository;
import com.reForm.backend.form.service.FormBuilderServiceImpl;
import com.reForm.backend.submission.dto.SubmissionResponseDto;
import com.reForm.backend.submission.entity.Submission;
import com.reForm.backend.submission.mapper.SubmissionMapper;
import com.reForm.backend.submission.port.ISubmissionProcessor;
import com.reForm.backend.submission.repository.SubmissionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubmissionProcessorImpl implements ISubmissionProcessor {

    private final SubmissionRepository repository;

    private final FormRepository formRepository;

    private final SubmissionMapper mapper;

    // Fix logic
    @Transactional
    @Override
    public SubmissionResponseDto saveSubmission(UUID formId, JsonNode answers, String ipAddress, String userAgent) {
        Form form = formRepository.findById(formId).orElseThrow(() -> new RuntimeException(formId + "not found!"));
        if (form.getStatus() == FormStatus.DRAFT){
            throw new RuntimeException("The form is not published.");
        }

        // Move to frontend
        for (AbstractBlock block : form.getBlocks()){
            String key = block.getId().toString();
            if (block.isRequired()){
                if (answers.get(key) == null) throw new RuntimeException("Field cannot be empty");
            }
        }
        Submission submission = new Submission();
        submission.setForm(form);
        submission.setAnswers(answers);
        submission.setSubmitterIp(ipAddress);
        submission.setUserAgent(userAgent);
        return mapper.toResponseDto(repository.save(submission));
    }
}
