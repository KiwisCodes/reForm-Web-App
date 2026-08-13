package com.reForm.backend.ai.agent;

import com.reForm.backend.ai.dto.AiConversationalBlockDto;
import com.reForm.backend.ai.event.FormLayoutModificationEvent;
import com.reForm.backend.ai.factory.BlockFactory;
import com.reForm.backend.form.entity.Form;
import com.reForm.backend.form.entity.block.AbstractBlock;
import com.reForm.backend.form.entity.block.conversationalBlock.ConversationalBlock;
import com.reForm.backend.form.entity.block.staticblock.standard.EmailStaticBlock;
import com.reForm.backend.form.entity.block.staticblock.standard.PhoneStaticBlock;
import com.reForm.backend.form.entity.block.staticblock.standard.ShortTextStaticBlock;
import com.reForm.backend.form.repository.FormRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * LAYOUT AGENT (Async Spring Event Listener & Mode 2 Co-Builder Engine Integration)
 * 
 * ROLE IN ARCHITECTURE:
 * Asynchronously processes FormLayoutModificationEvent instances published by
 * ModifyFormLayoutToolHandler during Mode 4 voice co-building or Mode 2 text chat sessions.
 * 
 * HOW IT WORKS:
 * 1. Receives FormLayoutModificationEvent on a non-blocking background thread pool (@Async).
 * 2. Fetches the target Form entity from PostgreSQL (FormRepository).
 * 3. Interprets the userIntent string (e.g. "ADD_CONTACT_SECTION", "ADD_RATING") and creates/mutates
 *    the corresponding AbstractBlock instances (ShortTextStaticBlock, EmailStaticBlock, ConversationalBlock).
 * 4. Persists the updated JSONB blocks array into PostgreSQL.
 * 5. Pushes a real-time notification log indicating canvas state has been updated.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LayoutAgent {

    private final FormRepository formRepository;
    private final BlockFactory blockFactory;

    /**
     * ASYNCHRONOUS EVENT LISTENER: FormLayoutModificationEvent
     * 
     * Runs on Spring's @Async worker thread pool to keep the real-time Gemini Live WebSocket
     * stream latency-free.
     */
    @Async
    @EventListener
    @Transactional
    public void handleLayoutModification(FormLayoutModificationEvent event) {
        log.info("🚀 [LAYOUT AGENT STARTED] Received FormLayoutModificationEvent for Form ID: {}, Intent: {}, SessionId: {}",
                 event.formId(), event.userIntent(), event.sessionId());

        if (event.formId() == null) {
            log.warn("⚠️ [LAYOUT AGENT SKIPPED] Form ID is null. Cannot modify layout.");
            return;
        }

        try {
            UUID formUuid = event.formId();
            Optional<Form> formOpt = formRepository.findById(formUuid);

            if (formOpt.isEmpty()) {
                log.warn("❌ [LAYOUT AGENT ERROR] Form not found in PostgreSQL for ID: {}", event.formId());
                return;
            }

            Form form = formOpt.get();
            String intent = event.userIntent() != null ? event.userIntent().toUpperCase() : "";

            if (event.targetBlocks() != null && !event.targetBlocks().isEmpty()) {
                for (AbstractBlock targetBlock : event.targetBlocks()) {
                    targetBlock.setSortOrder(form.getBlocks().size() + 1);
                    form.getBlocks().add(targetBlock);
                }
            } else {
                // Interpret userIntent and generate corresponding layout block
                AbstractBlock newBlock = createBlockFromIntent(intent);
                newBlock.setSortOrder(form.getBlocks().size() + 1);
                form.getBlocks().add(newBlock);
            }

            // Save updated Form entity to PostgreSQL
            formRepository.save(form);

            log.info("✅ [LAYOUT AGENT COMPLETED] Updated Form '{}' (Total Blocks: {})",
                     form.getTitle(), form.getBlocks().size());

        } catch (Exception e) {
            log.error("❌ [LAYOUT AGENT EXCEPTION] Failed to modify layout for Form ID: {}", event.formId(), e);
        }
    }

    /**
     * HELPER: Maps user intent strings to concrete AbstractBlock instances.
     * Integrates with Mode 2 schema creation logic and BlockFactory.
     */
    private AbstractBlock createBlockFromIntent(String intent) {
        if (intent.contains("CONTACT") || intent.contains("EMAIL")) {
            EmailStaticBlock block = new EmailStaticBlock();
            block.setLabel("Email Address");
            block.setDescription("Please enter your primary email address.");
            block.setRequired(true);
            return block;

        } else if (intent.contains("PHONE")) {
            PhoneStaticBlock block = new PhoneStaticBlock();
            block.setLabel("Phone Number");
            block.setDescription("Please enter your contact phone number.");
            block.setRequired(false);
            return block;

        } else if (intent.contains("CONVERSATIONAL") || intent.contains("INTERVIEW")) {
            AiConversationalBlockDto dto = new AiConversationalBlockDto();
            dto.setLabel("AI Verbal Interview Block");
            dto.setPrompt("Evaluate candidate background and key competencies.");
            dto.setPersona("Professional Technical Recruiter");
            dto.setMaxQuestions(5);
            dto.setRequired(true);
            return blockFactory.build(dto);

        } else {
            // Default Short Text Block fallback for general intents
            ShortTextStaticBlock block = new ShortTextStaticBlock();
            block.setLabel(formatLabelFromIntent(intent));
            block.setDescription("Please provide your response below.");
            block.setRequired(false);
            return block;
        }
    }

    /**
     * Formats raw user intent string into a human-readable block label.
     */
    private String formatLabelFromIntent(String intent) {
        String cleaned = intent.replace("ADD_", "").replace("_SECTION", "").replace("_BLOCK", "").replace("_", " ");
        if (cleaned.isBlank()) {
            return "New Form Field";
        }
        return cleaned.substring(0, 1).toUpperCase() + cleaned.substring(1).toLowerCase();
    }
}
