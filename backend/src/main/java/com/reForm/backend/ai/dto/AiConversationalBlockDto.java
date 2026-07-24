package com.reForm.backend.ai.dto;

public record AiConversationalBlockDto(
        String prompt,
        String persona,
        Integer maxQuestions
) implements AiBlockDto{
}
