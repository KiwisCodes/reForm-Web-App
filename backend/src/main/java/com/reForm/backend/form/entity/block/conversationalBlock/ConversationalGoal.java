package com.reForm.backend.form.entity.block.conversationalBlock;

/**
 * CONVERSATIONAL GOAL VALUE OBJECT
 * 
 * Represents a structured topic checklist goal that the AI persona must address during a
 * ConversationalBlock session. Tracked in Redis by MemoryGoalAgent.
 *
 * @param key             Unique goal key (e.g. "work_experience")
 * @param title           Short human-readable goal title (e.g. "Ask about past Java experience")
 * @param description     Detailed instruction for the AI (e.g. "Candidate must detail framework versions, team size, and role")
 * @param isRequired      Whether this goal must be satisfied before advancing
 * @param targetDataField Field key mapping the response value into the Submission entity
 */
public record ConversationalGoal(
    String key,
    String title,
    String description,
    boolean isRequired,
    String targetDataField
) {}
