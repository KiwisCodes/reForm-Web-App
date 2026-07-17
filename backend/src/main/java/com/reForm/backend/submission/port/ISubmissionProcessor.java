package com.reForm.backend.submission.port;

import tools.jackson.databind.JsonNode;
import com.reForm.backend.submission.dto.SubmissionResponseDto;

import java.util.UUID;

public interface ISubmissionProcessor {
    public SubmissionResponseDto saveSubmission(UUID formId, JsonNode answers, String userAgent);

}
