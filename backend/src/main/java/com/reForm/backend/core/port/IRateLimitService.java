package com.reForm.backend.core.port;
import com.reForm.backend.user.entity.Role;

public interface IRateLimitService {

    /**
     * Evaluates if a request should be allowed based on a key and their security Role.
     *
     * @param clientKey Unique identifier of the caller (User UUID or Client IP)
     * @param role The security role of the caller (null for unauthenticated guests)
     * @return true if a token was consumed, false if rate limits have been exhausted
     */
    boolean tryConsume(String clientKey, Role role);

    /**
     * Returns the duration in seconds the client must wait before their bucket receives more tokens.
     */
    long getWaitTimeInSeconds(String clientKey, Role role);
}