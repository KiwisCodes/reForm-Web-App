package com.reForm.backend.ai.service;

import com.reForm.backend.ai.port.IAiModelProviderStrategy;
import com.reForm.backend.ai.strategy.block.BlockExecutionRegistry;
import com.reForm.backend.form.repository.FormAiAgentProfileRepository;
import com.reForm.backend.user.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionContextServiceToolDeclarationTest {

    private SessionContextService sessionContextService;

    @BeforeEach
    void setUp() {
        FormAiAgentProfileRepository profileRepository = Mockito.mock(FormAiAgentProfileRepository.class);
        List<IAiModelProviderStrategy> modelStrategies = List.of();
        BlockExecutionRegistry blockExecutionRegistry = new BlockExecutionRegistry(List.of());

        sessionContextService = new SessionContextService(
                modelStrategies,
                profileRepository,
                blockExecutionRegistry
        );
    }

    @Test
    @DisplayName("Verify endSession tool declaration contains intelligent multi-trigger semantic description and enum reasons")
    void testEndSessionToolDeclaration() {
        List<Map<String, Object>> tools = sessionContextService.buildToolDeclarations(Role.FORM_FILLER, true, true);

        assertThat(tools).isNotEmpty();

        Map<String, Object> endSessionTool = tools.stream()
                .filter(t -> "endSession".equals(t.get("name")))
                .findFirst()
                .orElse(null);

        assertThat(endSessionTool).isNotNull();
        String description = (String) endSessionTool.get("description");
        assertThat(description).contains("Gracefully terminates the voice or text session");
        assertThat(description).contains("Task Complete");
        assertThat(description).contains("Natural Wrap-Up");
        assertThat(description).contains("Early Departure");
        assertThat(description).contains("DO NOT call this tool if the user is merely answering");

        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>) endSessionTool.get("parameters");
        assertThat(parameters).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) parameters.get("properties");
        assertThat(properties).containsKey("reason");
        assertThat(properties).containsKey("summary");
        assertThat(properties).containsKey("unresolvedItems");

        @SuppressWarnings("unchecked")
        Map<String, Object> reasonProp = (Map<String, Object>) properties.get("reason");
        @SuppressWarnings("unchecked")
        List<String> enums = (List<String>) reasonProp.get("enum");
        assertThat(enums).contains("COMPLETED_GOALS", "USER_WRAP_UP", "USER_ABORT_EARLY", "BUILDER_PUBLISHED_EXIT", "TIMEOUT");
    }
}
