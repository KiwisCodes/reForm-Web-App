package com.reForm.backend.ai.event;

import java.util.UUID;

/**
 * SESSION ENDED EVENT
 * 
 * Published upon termination of a filler or builder session to trigger post-session evaluation,
 * transcript persistence, and billing finalization.
 */
public record SessionEndedEvent(
    UUID sessionId,
    UUID formId,
    UUID submissionId,
    String closeReason
) {}
