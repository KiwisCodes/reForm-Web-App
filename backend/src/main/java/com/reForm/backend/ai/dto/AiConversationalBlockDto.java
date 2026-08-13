package com.reForm.backend.ai.dto;

import com.reForm.backend.form.entity.block.conversationalBlock.ConversationalGoal;
import com.reForm.backend.form.entity.block.conversationalBlock.RubricCriterion;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AiConversationalBlockDto extends AiBlockDto {

    private String prompt;
    private String persona;
    private String voiceName;
    private Integer maxQuestions;
    private List<ConversationalGoal> goals;
    private List<String> allowedToolNames;
    private Integer maxTurnCount;
    private List<RubricCriterion> evaluationRubric;
}
