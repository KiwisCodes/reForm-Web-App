package com.reForm.backend.ai.session;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Real Redis, no mocking — mirrors RedisConfig's actual serializer setup (GenericJacksonJsonRedisSerializer)
// exactly, so this proves ChatTurn genuinely round-trips through the same configuration the real
// app uses, not just an idealized in-memory stand-in. Requires the project's docker-compose Redis
// (localhost:6379) to be running.
class ChatSessionStoreTest {

    private static LettuceConnectionFactory connectionFactory;
    private RedisTemplate<String, Object> redisTemplate;
    private ChatSessionStore store;
    private String userId;
    private String sessionId;

    @BeforeAll
    static void startConnectionFactory() {
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 6379));
        connectionFactory.afterPropertiesSet();
    }

    @AfterAll
    static void stopConnectionFactory() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJacksonJsonRedisSerializer(new ObjectMapper()));
        redisTemplate.afterPropertiesSet();

        store = new ChatSessionStore(redisTemplate, new ObjectMapper());
        userId = "test-user-" + UUID.randomUUID();
        sessionId = "test-session-" + UUID.randomUUID();
    }

    @AfterEach
    void cleanUp() {
        redisTemplate.delete("chat:session:" + userId + ":" + sessionId);
    }

    @Test
    void appendTurnThenGetHistoryReturnsTurnsInOrder() {
        store.appendTurn(userId, sessionId, new ChatTurn(ChatRole.USER, "Add a name field"));
        store.appendTurn(userId, sessionId, new ChatTurn(ChatRole.MODEL, "Added a short text field for name"));

        List<ChatTurn> history = store.getHistory(userId, sessionId);

        assertEquals(2, history.size());
        assertEquals(new ChatTurn(ChatRole.USER, "Add a name field"), history.get(0));
        assertEquals(new ChatTurn(ChatRole.MODEL, "Added a short text field for name"), history.get(1));
    }

    @Test
    void getHistoryForUnknownSessionReturnsEmptyList() {
        List<ChatTurn> history = store.getHistory(userId, "nonexistent-" + UUID.randomUUID());
        assertTrue(history.isEmpty());
    }

    @Test
    void getHistoryForSameSessionIdUnderDifferentUserIsIsolated() {
        store.appendTurn(userId, sessionId, new ChatTurn(ChatRole.USER, "Add a name field"));

        String otherUserId = "test-user-" + UUID.randomUUID();
        List<ChatTurn> historyForOtherUser = store.getHistory(otherUserId, sessionId);

        assertTrue(historyForOtherUser.isEmpty());
    }
}
