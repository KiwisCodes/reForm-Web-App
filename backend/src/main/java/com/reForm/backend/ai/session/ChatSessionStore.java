package com.reForm.backend.ai.session;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

// Redis-backed conversation history for Mode 2 text chat, keyed by userId + sessionId. Mirrors
// SessionTracker's TTL-lease idiom (refresh expiry on every write) but stores an ordered list of
// turns instead of connection-presence metadata — a genuinely different need voice mode never had,
// since Gemini's own open Live connection holds that memory implicitly for Mode 4.
// userId is required on every call, sourced from the authenticated caller (never the client) —
// same boundary already enforced for workspaceId/creatorId elsewhere in ai.*. Namespacing the key
// by userId means a session under one user's namespace is structurally unreachable by another
// user's sessionId, even a guessed or reused one — there is no separate ownership check to forget.
@Service
@RequiredArgsConstructor
public class ChatSessionStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String SESSION_KEY_PREFIX = "chat:session:";
    private static final Duration SESSION_TTL = Duration.ofHours(2);

    public List<ChatTurn> getHistory(String userId, String sessionId) {
        String key = buildKey(userId, sessionId);
        List<Object> raw = redisTemplate.opsForList().range(key, 0, -1);
        if (raw == null) {
            return List.of();
        }
        // GenericJacksonJsonRedisSerializer deserializes back into a raw LinkedHashMap through the
        // generic RedisTemplate<String, Object> API, not the real ChatTurn type — same reasoning as
        // BlockFactory's objectMapper.convertValue for AbstractBlock, applied here to reconstruct
        // the actual record type ourselves instead of relying on the serializer's type-preservation.
        return raw.stream().map(item -> objectMapper.convertValue(item, ChatTurn.class)).toList();
    }

    public void appendTurn(String userId, String sessionId, ChatTurn turn) {
        String key = buildKey(userId, sessionId);
        redisTemplate.opsForList().rightPush(key, turn);
        redisTemplate.expire(key, SESSION_TTL);
    }

    private String buildKey(String userId, String sessionId) {
        return SESSION_KEY_PREFIX + userId + ":" + sessionId;
    }
}
