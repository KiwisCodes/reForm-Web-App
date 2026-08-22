package com.reForm.backend.ai.tool.port;

import java.util.UUID;

// Mode 2's transport-neutral replacement for the WebSocketSession context Mode 3/4 handlers read
// attributes off of — Mode 2 is stateless HTTP, so there's no session to carry this metadata for
// it. Built fresh per request in AiChatService from data already validated by FormSecurity, then
// passed straight through to whichever IMode2ToolHandler Mode2ToolRegistry dispatches to.
public record ChatToolExecutionContext(UUID formId, UUID workspaceId, UUID userId) {
}
