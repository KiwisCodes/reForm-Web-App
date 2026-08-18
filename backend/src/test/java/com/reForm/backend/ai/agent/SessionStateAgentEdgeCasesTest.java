package com.reForm.backend.ai.agent;

import com.reForm.backend.ai.domain.SessionPhase;
import com.reForm.backend.ai.domain.SessionStateRecoveryResult;
import com.reForm.backend.ai.domain.TranscriptTurn;
import com.reForm.backend.ai.event.SessionStateSnapshotEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SessionStateAgentEdgeCasesTest {

    private SessionStateAgent agent;
    private RedisTemplate<String, Object> mockRedisTemplate;
    private HashOperations<String, Object, Object> mockHashOps;
    private ListOperations<String, Object> mockListOps;
    private ValueOperations<String, Object> mockValueOps;
    private ObjectMapper objectMapper;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockRedisTemplate = Mockito.mock(RedisTemplate.class);
        mockHashOps = Mockito.mock(HashOperations.class);
        mockListOps = Mockito.mock(ListOperations.class);
        mockValueOps = Mockito.mock(ValueOperations.class);
        objectMapper = new ObjectMapper();

        when(mockRedisTemplate.opsForHash()).thenReturn(mockHashOps);
        when(mockRedisTemplate.opsForList()).thenReturn(mockListOps);
        when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOps);

        agent = new SessionStateAgent(mockRedisTemplate, objectMapper);
    }

    @Test
    @DisplayName("EC-2: Multi-tab simultaneous reconnect should reject second tab with LOCKED status")
    void testMultiTabLockContention() {
        String sessionId = "sess-split-brain";

        // First tab acquires lock; second tab fails
        when(mockValueOps.setIfAbsent(eq("sess:sess-split-brain:lock"), any(), any(Duration.class)))
                .thenReturn(false);

        SessionStateRecoveryResult result = agent.recoverSessionState(sessionId);

        assertThat(result.status()).isEqualTo(SessionStateRecoveryResult.Status.LOCKED);
        assertThat(result.memento()).isNull();
    }

    @Test
    @DisplayName("EC-3: Reconnecting after grace period expiration should return EXPIRED status")
    void testExpiredGracePeriod() {
        String sessionId = "sess-expired";

        when(mockValueOps.setIfAbsent(eq("sess:sess-expired:lock"), any(), any(Duration.class)))
                .thenReturn(true);
        when(mockHashOps.entries("sess:sess-expired:state")).thenReturn(null);

        SessionStateRecoveryResult result = agent.recoverSessionState(sessionId);

        assertThat(result.status()).isEqualTo(SessionStateRecoveryResult.Status.EXPIRED);
        assertThat(result.memento()).isNull();
    }

    @Test
    @DisplayName("EC-6: Circuit Breaker - L1 in-memory cache handles requests gracefully if Redis is down")
    void testCircuitBreakerFallbackToL1Cache() {
        String sessionId = "sess-degraded";

        // Simulate Redis down during initialization
        doThrow(new RedisConnectionFailureException("Redis connection timed out"))
                .when(mockHashOps).putAll(any(), any());

        agent.initializeSession(sessionId, "user-1", "form-1", "FORM_FILLER", "GEMINI_3_1_LIVE", "Puck");

        // Verify L1 cache fallback serves the state without throwing exception
        assertThat(agent.getSessionState(sessionId)).isNotNull();
        assertThat(agent.getSessionState(sessionId).sessionId()).isEqualTo(sessionId);
        assertThat(agent.getSessionState(sessionId).sessionPhase()).isEqualTo(SessionPhase.INIT);
    }

    @Test
    @DisplayName("EC-7: Rolling Window - Trims oldest turn when list exceeds 20 turns and appends to dialogueSummary")
    void testRollingTranscriptTrimming() {
        String sessionId = "sess-long-conversation";
        TranscriptTurn turn21 = new TranscriptTurn(21, "user", "21st turn answer", System.currentTimeMillis(), "q21");

        // Mock list size > 20
        when(mockListOps.size("sess:sess-long-conversation:transcript")).thenReturn(21L);
        when(mockListOps.leftPop("sess:sess-long-conversation:transcript")).thenReturn(
            "{\"turnId\":1,\"role\":\"user\",\"text\":\"Oldest turn 1\",\"timestamp\":100,\"activeBlockId\":\"q1\"}"
        );
        when(mockHashOps.get("sess:sess-long-conversation:state", "dialogueSummary")).thenReturn("Summary so far");

        agent.handleSnapshotEvent(new SessionStateSnapshotEvent(sessionId, turn21));

        // Verify leftPop was executed to trim the oldest turn
        verify(mockListOps).leftPop("sess:sess-long-conversation:transcript");
        // Verify dialogueSummary was updated with the evicted turn text
        verify(mockHashOps).put(eq("sess:sess-long-conversation:state"), eq("dialogueSummary"), contains("Oldest turn 1"));
    }

    @Test
    @DisplayName("EC-1: Rapid disconnects on already TERMINATED session should be ignored safely")
    void testRapidDisconnectAfterTerminated() {
        String sessionId = "sess-terminated";

        when(mockHashOps.get("sess:sess-terminated:state", "sessionPhase")).thenReturn("TERMINATED");

        agent.handleDisconnect(sessionId);

        // Should NOT overwrite TERMINATED with PAUSED
        verify(mockHashOps, never()).put(eq("sess:sess-terminated:state"), eq("sessionPhase"), eq("PAUSED"));
    }
}
