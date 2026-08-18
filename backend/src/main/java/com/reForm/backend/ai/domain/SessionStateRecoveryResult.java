package com.reForm.backend.ai.domain;

import java.util.List;

/**
 * SESSION STATE RECOVERY RESULT
 * 
 * Result record returned when a client attempts to recover an existing session after a network disconnect.
 */
public record SessionStateRecoveryResult(
    String sessionId,
    Status status,
    SessionStateMemento memento,
    List<TranscriptTurn> recentTurns,
    String message
) {
    public enum Status {
        SUCCESS,
        EXPIRED,
        NOT_FOUND,
        LOCKED
    }

    public static SessionStateRecoveryResult success(String sessionId, SessionStateMemento memento, List<TranscriptTurn> recentTurns) {
        return new SessionStateRecoveryResult(sessionId, Status.SUCCESS, memento, recentTurns, "Session recovered successfully.");
    }

    public static SessionStateRecoveryResult expired(String sessionId) {
        return new SessionStateRecoveryResult(sessionId, Status.EXPIRED, null, List.of(), "Reconnection grace window (5 minutes) has expired.");
    }

    public static SessionStateRecoveryResult notFound(String sessionId) {
        return new SessionStateRecoveryResult(sessionId, Status.NOT_FOUND, null, List.of(), "No active or paused session found for the given ID.");
    }

    public static SessionStateRecoveryResult locked(String sessionId) {
        return new SessionStateRecoveryResult(sessionId, Status.LOCKED, null, List.of(), "Session is currently locked by another active connection.");
    }
}
