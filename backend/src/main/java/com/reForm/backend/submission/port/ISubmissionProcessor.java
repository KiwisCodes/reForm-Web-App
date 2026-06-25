package com.reForm.backend.submission.port;

import com.fasterxml.jackson.databind.JsonNode;
import com.reForm.backend.submission.dto.SubmissionResponseDto;

import java.util.UUID;

public interface ISubmissionProcessor {
    public SubmissionResponseDto saveSubmission(UUID formId, JsonNode answers, String ipAddress, String userAgent);

}
