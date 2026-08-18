package com.reForm.backend.ai.tool;

import com.reForm.backend.ai.tool.handler.universal.EndSessionToolHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class EndSessionToolHandlerTest {

    private EndSessionToolHandler handler;
    private ObjectMapper objectMapper;
    private org.springframework.context.ApplicationEventPublisher mockPublisher;
    private WebSocketSession mockClientSession;
    private Map<String, Object> sessionAttributes;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockPublisher = Mockito.mock(org.springframework.context.ApplicationEventPublisher.class);
        handler = new EndSessionToolHandler(objectMapper, mockPublisher);
        mockClientSession = Mockito.mock(WebSocketSession.class);
        sessionAttributes = new HashMap<>();

        when(mockClientSession.getId()).thenReturn("test-session-123");
        when(mockClientSession.getAttributes()).thenReturn(sessionAttributes);
        when(mockClientSession.isOpen()).thenReturn(true);
    }

    @Test
    @DisplayName("Verify getFunctionName returns 'endSession'")
    void testGetFunctionName() {
        assertThat(handler.getFunctionName()).isEqualTo("endSession");
    }

    @Test
    @DisplayName("Verify execute sets isEndingSession flag and returns SESSION_ENDING response")
    void testExecuteSetsEndingFlagAndReturnsResponse() throws Exception {
        String jsonString = """
            {
                "name": "endSession",
                "args": {
                    "reason": "COMPLETED_GOALS",
                    "summary": "Candidate completed all 5 technical interview questions."
                }
            }
            """;
        JsonNode functionCall = objectMapper.readTree(jsonString);

        Map<String, Object> result = handler.execute(mockClientSession, functionCall, "call-xyz-456");

        // Verify session attribute tag
        assertThat(sessionAttributes.get("isEndingSession")).isEqualTo(Boolean.TRUE);

        // Verify response payload matching Gemini schema
        assertThat(result).isNotNull();
        assertThat(result.get("id")).isEqualTo("call-xyz-456");
        assertThat(result.get("name")).isEqualTo("endSession");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = (Map<String, Object>) result.get("response");
        assertThat(responseMap).isNotNull();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) responseMap.get("result");
        assertThat(resultMap).isNotNull();
        assertThat(resultMap.get("status")).isEqualTo("SESSION_ENDING");
    }
}
