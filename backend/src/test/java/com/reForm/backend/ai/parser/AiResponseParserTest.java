package com.reForm.backend.ai.parser;

import com.reForm.backend.ai.dto.AiBlockDto;
import com.reForm.backend.ai.dto.AiConversationalBlockDto;
import com.reForm.backend.ai.dto.AiFormDto;
import com.reForm.backend.ai.dto.AiStaticBlockDto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

// Real ObjectMapper, no mocking — AiFormDto/AiBlockDto's polymorphism is plain @JsonTypeInfo
// annotation-driven (no custom deserializer involved, unlike AbstractBlock), so no extra module
// registration is needed here the way FormFactoryTest needed for AbstractBlockJacksonConfig.
class AiResponseParserTest {

    private final AiResponseParser parser = new AiResponseParser(JsonMapper.builder().build());

    @Test
    void parseFormDispatchesTitleAndMixedBlockCategories() {
        String rawJson = """
                {
                  "title": "Customer Feedback",
                  "blocks": [
                    {"category":"STATIC","staticType":"SHORT_TEXT","label":"What is your name?","required":true},
                    {"category":"CONVERSATIONAL","label":"Follow-up","required":false,"prompt":"Tell me more","persona":"friendly","maxQuestions":2}
                  ]
                }
                """;

        AiFormDto result = parser.parseForm(rawJson);

        assertEquals("Customer Feedback", result.title());
        assertEquals(2, result.blocks().size());

        AiStaticBlockDto first = assertInstanceOf(AiStaticBlockDto.class, result.blocks().get(0));
        assertEquals("SHORT_TEXT", first.getStaticType());
        assertEquals("What is your name?", first.getLabel());

        AiConversationalBlockDto second = assertInstanceOf(AiConversationalBlockDto.class, result.blocks().get(1));
        assertEquals("Tell me more", second.getPrompt());
        assertEquals("friendly", second.getPersona());
        assertEquals(2, second.getMaxQuestions());
    }

    @Test
    void parseBlocksDispatchesEachElementByCategoryAndKeepsUnmappedAttributes() {
        String rawJson = """
                [
                  {"category":"STATIC","staticType":"CHOICE","label":"Favorite color?","required":false,"selectionType":"DROPDOWN","options":["Red","Blue"]}
                ]
                """;

        List<AiBlockDto> result = parser.parseBlocks(rawJson);

        assertEquals(1, result.size());
        AiStaticBlockDto dto = assertInstanceOf(AiStaticBlockDto.class, result.get(0));
        assertEquals("CHOICE", dto.getStaticType());
        assertEquals("DROPDOWN", dto.getAdditionalProperties().get("selectionType"));
        assertEquals(List.of("Red", "Blue"), dto.getAdditionalProperties().get("options"));
    }
}
