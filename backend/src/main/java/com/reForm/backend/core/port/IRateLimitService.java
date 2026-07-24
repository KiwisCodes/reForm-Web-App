package com.reForm.backend.core.port;

import com.reForm.backend.user.entity.Role;

/**
 * RATE LIMIT SERVICE PORT (Interface)
 * 
 * Defines the contract for distributed token-bucket rate limiting logic.
 */
public interface IRateLimitService {

    /**
     * Attempts to consume 1 token from the client's rate-limiting bucket in Redis.
     * 
     * @param clientKey Unique client identifier (IP address or User UUID)
     * @param role User security Role (FORM_BUILDER, FORM_FILLER, ADMIN, or null for guests)
     * @return true if token was available and consumed; false if rate limit breached
     */
    boolean tryConsume(String clientKey, Role role);

    /**
     * Calculates the estimated backoff wait duration in seconds before the bucket will refill enough tokens.
     * 
     * @param clientKey Unique client identifier
     * @param role User security Role
     * @return Wait time in seconds (minimum 1)
     */
    long getWaitTimeInSeconds(String clientKey, Role role);
}
