package com.reForm.backend.ai.domain;

/**
 * TRANSCRIPT TURN RECORD
 * 
 * Immutable record representing a single conversational turn (user speech or AI speech).
 * Appended to Hot Redis RAM list (sess:{sessionId}:transcript) for low-latency dialogue history.
 */
public record TranscriptTurn(
    int turnId,
    String role, // "user" or "model"
    String text,
    long timestamp,
    String activeBlockId
) {}
