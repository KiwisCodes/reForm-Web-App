package com.reForm.backend.ai.event;

import java.util.UUID;

/**
 * GUARDRAIL VALIDATION EVENT
 * 
 * Published during active session processing to validate input/output text
 * for safety, toxicity, and prompt injection via GuardrailAgent.
 */
public record GuardrailValidationEvent(
    UUID sessionId,
    String inputContent,
    String direction
) {}
