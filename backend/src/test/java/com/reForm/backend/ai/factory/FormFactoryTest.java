package com.reForm.backend.ai.factory;

import com.reForm.backend.ai.dto.AiConversationalBlockDto;
import com.reForm.backend.ai.dto.AiFormDto;
import com.reForm.backend.ai.dto.AiStaticBlockDto;
import com.reForm.backend.form.dto.FormCreateDto;
import com.reForm.backend.form.entity.block.AbstractBlock;
import com.reForm.backend.form.entity.block.AbstractBlockDeserializer;
import com.reForm.backend.form.entity.block.conversationalBlock.ConversationalBlock;
import com.reForm.backend.form.entity.block.staticblock.standard.ShortTextStaticBlock;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Uses a real BlockFactory (real Jackson ObjectMapper, no mocking) so this exercises the actual
// AiFormDto -> FormCreateDto conversion end to end, the same way BlockSchemaGeneratorTest verifies
// real reflection rather than a mocked stand-in.
class FormFactoryTest {

    // Mirrors AbstractBlockJacksonConfig's module registration — this ObjectMapper is built
    // locally, not via Spring, so it needs the same module added explicitly.
    private static SimpleModule abstractBlockModule() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(AbstractBlock.class, new AbstractBlockDeserializer());
        return module;
    }

    private final BlockFactory blockFactory =
            new BlockFactory(JsonMapper.builder().addModule(abstractBlockModule()).build());
    private final FormFactory formFactory = new FormFactory(blockFactory);

    @Test
    void buildCreateDtoConvertsTitleAndEachBlockAndUsesTheExplicitWorkspaceIdNotAiFormDto() {
        AiStaticBlockDto shortText = new AiStaticBlockDto();
        shortText.setStaticType("SHORT_TEXT");
        shortText.setLabel("What is your name?");
        shortText.setRequired(true);

        AiConversationalBlockDto interview = new AiConversationalBlockDto();
        interview.setLabel("Follow-up chat");
        interview.setRequired(false);
        interview.setPrompt("Tell me about your experience");
        interview.setPersona("friendly recruiter");
        interview.setMaxQuestions(3);

        AiFormDto aiForm = new AiFormDto("Customer Feedback", List.of(shortText, interview));
        UUID workspaceId = UUID.randomUUID();

        FormCreateDto result = formFactory.buildCreateDto(aiForm, workspaceId);

        assertEquals("Customer Feedback", result.title());
        assertEquals(workspaceId, result.workspaceId());
        assertEquals(2, result.blocks().size());

        assertInstanceOf(ShortTextStaticBlock.class, result.blocks().get(0));
        ShortTextStaticBlock builtShortText = (ShortTextStaticBlock) result.blocks().get(0);
        assertEquals("What is your name?", builtShortText.getLabel());
        assertTrue(builtShortText.isRequired());

        assertInstanceOf(ConversationalBlock.class, result.blocks().get(1));
        ConversationalBlock builtInterview = (ConversationalBlock) result.blocks().get(1);
        assertEquals("Tell me about your experience", builtInterview.getPrompt());
        assertEquals("friendly recruiter", builtInterview.getPersona());
        assertEquals(3, builtInterview.getMaxQuestions());
    }

    @Test
    void buildCreateDtoWithEmptyBlockListProducesAFormWithNoBlocks() {
        AiFormDto aiForm = new AiFormDto("Empty Form", List.of());
        UUID workspaceId = UUID.randomUUID();

        FormCreateDto result = formFactory.buildCreateDto(aiForm, workspaceId);

        assertEquals("Empty Form", result.title());
        assertTrue(result.blocks().isEmpty());
    }
}
