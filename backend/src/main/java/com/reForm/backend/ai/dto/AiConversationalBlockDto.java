package com.reForm.backend.ai.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiConversationalBlockDto extends AiBlockDto {

    private String prompt;
    private String persona;
    private Integer maxQuestions;
}
