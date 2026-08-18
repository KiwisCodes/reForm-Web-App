package com.reForm.backend.ai.agent;

import com.reForm.backend.ai.domain.SessionPhase;
import com.reForm.backend.ai.domain.SessionStateMemento;
import com.reForm.backend.ai.domain.SessionStateRecoveryResult;
import com.reForm.backend.ai.domain.TranscriptTurn;
import com.reForm.backend.ai.event.SessionEndedEvent;
import com.reForm.backend.ai.event.SessionStateSnapshotEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SessionStateAgentTest {

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
    @DisplayName("initializeSession should store state hash and set 2-hour TTL lease")
    void testInitializeSession() {
        String sessionId = "sess-123";
        agent.initializeSession(sessionId, "user-1", "form-1", "FORM_FILLER", "GEMINI_3_1_LIVE", "Puck");

        verify(mockHashOps).putAll(eq("sess:sess-123:state"), any(Map.class));
        verify(mockRedisTemplate).expire(eq("sess:sess-123:state"), eq(Duration.ofHours(2)));

        SessionStateMemento state = agent.getSessionState(sessionId);
        assertThat(state).isNotNull();
        assertThat(state.sessionId()).isEqualTo(sessionId);
        assertThat(state.sessionPhase()).isEqualTo(SessionPhase.INIT);
    }

    @Test
    @DisplayName("markSessionActive should transition phase to ACTIVE")
    void testMarkSessionActive() {
        String sessionId = "sess-123";
        agent.initializeSession(sessionId, "user-1", "form-1", "FORM_FILLER", "GEMINI_3_1_LIVE", "Puck");
        agent.markSessionActive(sessionId);

        verify(mockHashOps).put(eq("sess:sess-123:state"), eq("sessionPhase"), eq("ACTIVE"));
        verify(mockRedisTemplate, atLeastOnce()).expire(eq("sess:sess-123:state"), eq(Duration.ofHours(2)));
    }

    @Test
    @DisplayName("handleDisconnect should transition phase to PAUSED and set 5-minute grace TTL")
    void testHandleDisconnect() {
        String sessionId = "sess-123";
        agent.initializeSession(sessionId, "user-1", "form-1", "FORM_FILLER", "GEMINI_3_1_LIVE", "Puck");
        agent.handleDisconnect(sessionId);

        verify(mockHashOps).put(eq("sess:sess-123:state"), eq("sessionPhase"), eq("PAUSED"));
        verify(mockRedisTemplate).expire(eq("sess:sess-123:state"), eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("recordAnswer should update answers JSON in Redis hash")
    void testRecordAnswer() {
        String sessionId = "sess-123";
        when(mockHashOps.get("sess:sess-123:state", "answers")).thenReturn("{\"previous\":\"old\"}");

        agent.recordAnswer(sessionId, "experience", 5);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mockHashOps).put(eq("sess:sess-123:state"), eq("answers"), captor.capture());

        String savedJson = captor.getValue();
        assertThat(savedJson).contains("\"experience\":5");
        assertThat(savedJson).contains("\"previous\":\"old\"");
    }

    @Test
    @DisplayName("recordSkippedField should add field ID to skippedFields set")
    void testRecordSkippedField() {
        String sessionId = "sess-123";
        when(mockHashOps.get("sess:sess-123:state", "skippedFields")).thenReturn("[\"block-1\"]");

        agent.recordSkippedField(sessionId, "block-2", "USER_DECLINED");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mockHashOps).put(eq("sess:sess-123:state"), eq("skippedFields"), captor.capture());

        String savedJson = captor.getValue();
        assertThat(savedJson).contains("block-1");
        assertThat(savedJson).contains("block-2");
    }

    @Test
    @DisplayName("recoverSessionState should restore memento and return SUCCESS when valid")
    void testRecoverSessionState() {
        String sessionId = "sess-123";
        when(mockValueOps.setIfAbsent(eq("sess:sess-123:lock"), any(), any(Duration.class))).thenReturn(true);

        Map<Object, Object> rawMap = new HashMap<>();
        rawMap.put("sessionId", sessionId);
        rawMap.put("userId", "user-1");
        rawMap.put("formId", "form-1");
        rawMap.put("role", "FORM_FILLER");
        rawMap.put("modelKey", "GEMINI_3_1_LIVE");
        rawMap.put("sessionPhase", "PAUSED");
        rawMap.put("turnCount", "4");
        rawMap.put("answers", "{\"name\":\"Alice\"}");
        rawMap.put("skippedFields", "[]");
        rawMap.put("activeBlockId", "block-experience");

        when(mockHashOps.entries("sess:sess-123:state")).thenReturn(rawMap);
        when(mockListOps.range("sess:sess-123:transcript", 0, -1)).thenReturn(List.of(
            "{\"turnId\":1,\"role\":\"user\",\"text\":\"Hello\",\"timestamp\":1000,\"activeBlockId\":\"\"}"
        ));

        SessionStateRecoveryResult result = agent.recoverSessionState(sessionId);

        assertThat(result.status()).isEqualTo(SessionStateRecoveryResult.Status.SUCCESS);
        assertThat(result.memento()).isNotNull();
        assertThat(result.memento().turnCount()).isEqualTo(4);
        assertThat(result.memento().answers()).containsEntry("name", "Alice");
        assertThat(result.recentTurns()).hasSize(1);
        assertThat(result.recentTurns().get(0).text()).isEqualTo("Hello");
    }

    @Test
    @DisplayName("terminateSession should transition phase to TERMINATED and set 60-second eviction TTL")
    void testTerminateSession() {
        String sessionId = "sess-123";
        agent.terminateSession(sessionId, "COMPLETED_GOALS");

        verify(mockHashOps).put(eq("sess:sess-123:state"), eq("sessionPhase"), eq("TERMINATED"));
        verify(mockRedisTemplate).expire(eq("sess:sess-123:state"), eq(Duration.ofSeconds(60)));
        verify(mockRedisTemplate).expire(eq("sess:sess-123:transcript"), eq(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("handleSnapshotEvent should append turn to Redis List and refresh TTL")
    void testHandleSnapshotEvent() {
        String sessionId = "sess-123";
        TranscriptTurn turn = new TranscriptTurn(3, "user", "I am a Java developer", System.currentTimeMillis(), "q1");

        when(mockListOps.size("sess:sess-123:transcript")).thenReturn(5L);

        agent.handleSnapshotEvent(new SessionStateSnapshotEvent(sessionId, turn));

        verify(mockListOps).rightPush(eq("sess:sess-123:transcript"), contains("I am a Java developer"));
        verify(mockHashOps).put(eq("sess:sess-123:state"), eq("turnCount"), eq(3));
        verify(mockRedisTemplate, atLeastOnce()).expire(eq("sess:sess-123:transcript"), eq(Duration.ofHours(2)));
    }
}
