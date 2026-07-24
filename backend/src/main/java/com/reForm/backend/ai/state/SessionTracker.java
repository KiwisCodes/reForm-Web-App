package com.reForm.backend.ai.state;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * REDIS SESSION TRACKER SERVICE
 * 
 * Manages active WebSocket session presence metadata across distributed server nodes using Redis Hashes.
 * Ensures active user sessions are tracked with an explicit TTL lease to prevent ghost key accumulation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionTracker {

    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String SESSION_KEY_PREFIX = "session:";
    private static final Duration SESSION_TTL = Duration.ofHours(2);
    private final String nodeId = "NODE_1"; // Node identifier

    /**
     * Registers an active user's WebSocket session metadata in Redis.
     * Applies a 2-hour TTL lease immediately to prevent ghost sessions if the server crashes.
     */
    public void registerSession(String userId, String sessionId) {
        String sessionKey = SESSION_KEY_PREFIX + userId;
        Map<String, Object> payload = Map.of(
                "sessionId", sessionId,
                "connectedAt", System.currentTimeMillis(),
                "nodeId", nodeId
        );

        redisTemplate.opsForHash().putAll(sessionKey, payload);
        // FIX: Explicitly apply TTL expiration lease to the hash key
        redisTemplate.expire(sessionKey, SESSION_TTL);
        
        log.info("Registered active WebSocket session for user: {} on node: {} (Session ID: {})", 
                 userId, nodeId, sessionId);
    }

    /**
     * Refreshes the Redis TTL expiration timer back to 2 hours when client sends heartbeat PING frames.
     */
    public void refreshTTL(String userId) {
        String sessionKey = SESSION_KEY_PREFIX + userId;
        Boolean exists = redisTemplate.hasKey(sessionKey);
        if (Boolean.TRUE.equals(exists)) {
            redisTemplate.expire(sessionKey, SESSION_TTL);
            log.debug("Refreshed session TTL lease for user: {}", userId);
        }
    }

    /**
     * Checks if a user currently has an active WebSocket session registered in Redis.
     */
    public boolean isActive(String userId) {
        String sessionKey = SESSION_KEY_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey));
    }

    /**
     * Removes user session metadata from Redis when the client disconnects.
     */
    public void deregisterSession(String userId) {
        String sessionKey = SESSION_KEY_PREFIX + userId;
        redisTemplate.delete(sessionKey);
        log.info("Deregistered WebSocket session for user: {}", userId);
    }
}
