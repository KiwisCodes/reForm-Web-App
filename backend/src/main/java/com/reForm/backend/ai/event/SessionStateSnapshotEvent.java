package com.reForm.backend.ai.event;

import com.reForm.backend.ai.domain.TranscriptTurn;

import java.util.Map;

/**
 * SESSION STATE SNAPSHOT EVENT
 * 
 * Published asynchronously after each conversational turn or field update to capture
 * low-latency working snapshots in Hot Redis RAM.
 */
public record SessionStateSnapshotEvent(
    String sessionId,
    TranscriptTurn turn,
    Map<String, Object> updatedAnswers
) {
    public SessionStateSnapshotEvent(String sessionId, TranscriptTurn turn) {
        this(sessionId, turn, null);
    }
}
