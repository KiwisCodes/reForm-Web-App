package com.reForm.backend.ai.session;

// One message in a Mode 2 conversation. The basic unit ChatSessionStore persists and
// FormChatPromptBuilder consumes to assemble a Gemini request.
// No formId field by design, but not because sessions are form-independent in general — Mode 2
// splits into two flows: a workspace-level stats chat (genuinely form-independent, deferred) and a
// create/update chat that's bound to exactly one form for its whole lifetime (entered only by
// clicking "create form" or navigating to an existing one). Even for that second flow, formId
// still doesn't belong here: it's a per-request, security-relevant value, supplied on each call
// and validated against real ownership (mirroring FormSecurity/@PreAuthorize on BuilderController),
// never something to trust just because it's part of a stored turn.
public record ChatTurn(ChatRole role, String content) {
}
