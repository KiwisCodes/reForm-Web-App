package com.reForm.backend.form.entity.block.conversationalBlock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.reForm.backend.form.entity.block.AbstractBlock;
import com.reForm.backend.form.entity.block.BlockType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CONVERSATIONAL BLOCK ENTITY
 * 
 * Reached via AbstractBlockDeserializer when "type" == "CONVERSATIONAL".
 * Serves as a first-class conversational micro-session engine configuration:
 * 1. Persona & Prompting (system prompt, persona override)
 * 2. Voice Override (voiceName)
 * 3. Goal Memory Schema (goals checklist for MemoryGoalAgent)
 * 4. RAG Scoping (ragDocumentIds for RagSearchAgent)
 * 5. Tool Call Scoping (allowedToolNames whitelist for ToolCallRegistry)
 * 6. Execution Guardrails (maxQuestions, maxTurnCount, silenceTimeoutSeconds, autoAdvanceOnGoalCompletion)
 * 7. Post-Session Evaluation Rubric (evaluationRubric for EvaluationAgent)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
public class ConversationalBlock extends AbstractBlock {

    // 1. Core Persona & Prompting Overrides (Level 3 Cascade)
    private String prompt;
    private String persona;
    private String voiceName; // Optional block-level voice override (e.g. "Puck", "Kore")

    // 2. Goal Tracking (For MemoryGoalAgent & Redis Checklist)
    private List<ConversationalGoal> goals = new ArrayList<>();
    private List<UUID> ragDocumentIds = new ArrayList<>(); // Scope vector search to specific docs

    // 3. Tool Scoping & Security (For ToolCallRegistry)
    private List<String> allowedToolNames = new ArrayList<>(); // Whitelist of enabled tool names (empty = all)

    // 4. Execution Guardrails & Turn Controls
    private Integer maxQuestions;
    private Integer maxTurnCount;
    private Integer silenceTimeoutSeconds;
    private boolean autoAdvanceOnGoalCompletion = true;

    // 5. Post-Session Evaluation Rubric (For EvaluationAgent)
    private List<RubricCriterion> evaluationRubric = new ArrayList<>();

    @Override
    public BlockType getType() {
        return BlockType.CONVERSATIONAL;
    }
}
