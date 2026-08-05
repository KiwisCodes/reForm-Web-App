package com.reForm.backend.ai.config;

import com.reForm.backend.form.entity.Form;
import com.reForm.backend.form.entity.FormAiAgentProfile;
import com.reForm.backend.form.entity.FormStatus;
import com.reForm.backend.form.repository.FormAiAgentProfileRepository;
import com.reForm.backend.form.repository.FormRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * FORM AI DATA INITIALIZER (Database Seeder for Prompt & Voice Profiles)
 * 
 * WHY IS THIS NEEDED?
 * Seeds production-ready FormAiAgentProfile records into PostgreSQL table 'form_ai_agent_profiles'
 * on application startup so you can test live database prompt loading without hardcoded fallbacks!
 * 
 * TEST FORMS CREATED:
 * 1. Form Candidate Interviewer (UUID: 11111111-1111-1111-1111-111111111111)
 *    - Voice: Kore (Calm Female)
 *    - Persona Prompt: Cheerful, funny, and welcoming AI Recruiter.
 * 
 * 2. Form Builder Co-Pilot (UUID: 22222222-2222-2222-2222-222222222222)
 *    - Voice: Puck (Energetic Male)
 *    - Persona Prompt: Voice Form Architect Co-Pilot with modifyFormLayout tool calling.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FormAiDataInitializer implements CommandLineRunner {

    public static final UUID SAMPLE_FILLER_FORM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID SAMPLE_BUILDER_FORM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final FormRepository formRepository;
    private final FormAiAgentProfileRepository profileRepository;

    @Override
    public void run(String... args) {
        log.info("[FormAiDataInitializer]: Seeding database with FormAiAgentProfile prompts for testing...");

        try {
            seedFillerFormProfile();
            seedBuilderFormProfile();
            log.info("[FormAiDataInitializer]: ✅ Database seeding complete! Form profiles available in PostgreSQL.");
        } catch (Exception e) {
            log.warn("[FormAiDataInitializer]: Seeding skipped or already initialized: {}", e.getMessage());
        }
    }

    private void seedFillerFormProfile() {
        if (profileRepository.findByFormId(SAMPLE_FILLER_FORM_ID).isPresent()) {
            return;
        }

        Form form = formRepository.findById(SAMPLE_FILLER_FORM_ID).orElseGet(() -> {
            Form newForm = new Form();
            newForm.setId(SAMPLE_FILLER_FORM_ID);
            newForm.setTitle("Senior Java Engineer Voice Interview");
            newForm.setSlug("senior-java-interview");
            newForm.setStatus(FormStatus.PUBLISHED);
            return formRepository.save(newForm);
        });

        String nl = System.lineSeparator();

        FormAiAgentProfile profile = FormAiAgentProfile.builder()
                .form(form)
                .modelKey("GEMINI_3_1_LIVE")
                .voiceName("Kore") // Calm Female Voice
                .temperature(0.7f)
                .systemPromptTemplate(
                    "[ROLE & PERSONA]" + nl +
                    "You are an AI Technical Recruiter conducting an interactive voice interview for the Senior Java Engineer position." + nl + nl +
                    "[CUSTOM PERSONA TWIST]" + nl +
                    "Tone: Cheerful, funny, and welcoming! Use light humor and warm encouragement after every candidate response." + nl + nl +
                    "[CONVERSATIONAL GUIDELINES]" + nl +
                    "1. Speak naturally, warmly, and keep responses concise (under 25 words per turn)." + nl +
                    "2. Ask questions step-by-step (e.g. Years of Java experience, Spring Boot, Microservices)." + nl +
                    "3. If the candidate interrupts, stop speaking immediately and listen to their response."
                )
                .build();

        profileRepository.save(profile);
        log.info("[FormAiDataInitializer]: Seeded Form Candidate Interviewer profile (Form ID: {})", SAMPLE_FILLER_FORM_ID);
    }

    private void seedBuilderFormProfile() {
        if (profileRepository.findByFormId(SAMPLE_BUILDER_FORM_ID).isPresent()) {
            return;
        }

        String nl = System.lineSeparator();

        Form form = formRepository.findById(SAMPLE_BUILDER_FORM_ID).orElseGet(() -> {
            Form newForm = new Form();
            newForm.setId(SAMPLE_BUILDER_FORM_ID);
            newForm.setTitle("Interactive Form Builder Canvas");
            newForm.setSlug("interactive-form-builder");
            newForm.setStatus(FormStatus.DRAFT);
            return formRepository.save(newForm);
        });

        FormAiAgentProfile profile = FormAiAgentProfile.builder()
                .form(form)
                .modelKey("GEMINI_3_1_LIVE")
                .voiceName("Puck") // Energetic Male Voice
                .temperature(0.7f)
                .systemPromptTemplate(
                    "[ROLE & PERSONA]" + nl +
                    "You are an expert AI Form Architect Co-Pilot assisting a Form Creator in real time over voice." + nl + nl +
                    "[CAPABILITIES & TOOL CALLING]" + nl +
                    "You have access to the `modifyFormLayout` tool. Whenever the user asks to add, remove, or edit form fields, execute `modifyFormLayout`." + nl + nl +
                    "[VOICE CONVERSATION GUIDELINES]" + nl +
                    "1. Keep spoken responses short, natural, and under 2 sentences." + nl +
                    "2. Acknowledge user requests immediately and explain what field was added or updated on the canvas."
                )
                .build();

        profileRepository.save(profile);
        log.info("[FormAiDataInitializer]: Seeded Form Builder Co-Pilot profile (Form ID: {})", SAMPLE_BUILDER_FORM_ID);
    }
}
