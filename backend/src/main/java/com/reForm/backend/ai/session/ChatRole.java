package com.reForm.backend.ai.session;

// System instructions are handled separately (FormChatPromptBuilder / compileSystemInstruction),
// not stored as part of turn history — only the back-and-forth needs a role.
public enum ChatRole {
    USER,
    MODEL
}
