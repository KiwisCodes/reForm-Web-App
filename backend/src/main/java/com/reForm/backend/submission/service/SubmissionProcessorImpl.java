package com.reForm.backend.submission.service;

import tools.jackson.databind.JsonNode;
import com.reForm.backend.core.exception.ResourceNotAccessedException;
import com.reForm.backend.form.dto.BlockValidationRule;
import com.reForm.backend.form.dto.FormSubmissionDto;
import com.reForm.backend.form.entity.FormStatus;
import com.reForm.backend.form.port.IFormQueryPort;
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

    private final IFormQueryPort queryService;

    private final SubmissionMapper mapper;

    // Applying DDD rules here
    @Transactional
    @Override
    public SubmissionResponseDto saveSubmission(UUID formId, JsonNode answers, String userAgent) {
        FormSubmissionDto form = queryService.fetchForm(formId);
        if (form.status() == FormStatus.DRAFT){
            throw new ResourceNotAccessedException("The form is not published.");
        }

        for (BlockValidationRule rule : form.rules()){
            String key = rule.id().toString();
            if (rule.isRequired()){
                if (answers.path(key).asString().isBlank()) throw new ResourceNotAccessedException("Field cannot be empty");
            }
        }


        Submission submission = new Submission();
        submission.setFormId(formId);
        submission.setAnswers(answers);
//      submission.setSubmitterIp(ipAddress);
        submission.setUserAgent(userAgent);
        return mapper.toResponseDto(repository.save(submission));
    }
}
