package com.reForm.backend.ai.event;

import java.util.UUID;

/**
 * BILLING USAGE EVENT
 * 
 * Published asynchronously when metered system resources (voice seconds, LLM tokens, vector searches)
 * are consumed to track workspace credit balance.
 */
public record BillingUsageEvent(
    UUID workspaceId,
    UUID sessionId,
    String meterType,
    long unitsUsed
) {}
