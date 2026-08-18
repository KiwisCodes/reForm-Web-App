package com.reForm.backend.ai.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * SESSION STATE MEMENTO RECORD
 * 
 * Immutable snapshot record holding the complete in-flight working state of a live session.
 * Managed by SessionStateAgent as the Memento in the Memento Pattern.
 */
public record SessionStateMemento(
    String sessionId,
    String formId,
    String userId,
    String role,
    String modelKey,
    String voiceName,
    SessionPhase sessionPhase,
    String activeBlockId,
    int activeBlockIndex,
    int turnCount,
    Map<String, Object> answers,
    Set<String> skippedFields,
    String dialogueSummary,
    int lastCompletedTurnId,
    long connectedAt,
    long lastActiveAt
) {
    public static SessionStateMemento empty(String sessionId) {
        long now = Instant.now().toEpochMilli();
        return new SessionStateMemento(
            sessionId, null, null, null, null, null,
            SessionPhase.INIT, null, 0, 0,
            Map.of(), Set.of(), "", 0,
            now, now
        );
    }
}
