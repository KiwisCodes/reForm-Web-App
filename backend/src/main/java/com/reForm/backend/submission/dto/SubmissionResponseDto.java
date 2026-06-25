package com.reForm.backend.submission.dto;

import java.util.UUID;

public record SubmissionResponseDto(
        String message,
        UUID submissionId
) {
}
