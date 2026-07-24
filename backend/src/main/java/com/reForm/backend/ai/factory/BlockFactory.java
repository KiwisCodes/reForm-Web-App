package com.reForm.backend.ai.factory;

import com.reForm.backend.ai.dto.AiBlockDto;
import com.reForm.backend.ai.dto.AiConversationalBlockDto;
import com.reForm.backend.ai.dto.AiStaticBlockDto;
import com.reForm.backend.form.entity.block.AbstractBlock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

// Merges each AiBlockDto's known fields (plus AiStaticBlockDto's additionalProperties catch-all)
// into a plain Map, then hands it to AbstractBlock's own dispatch — AbstractBlockDeserializer
// branches on "type" (STATIC -> StaticBlock's "staticType" leaves, CONVERSATIONAL ->
// ConversationalBlock) — instead of a hand-written switch + setter per block attribute. See
// week3/custom-abstractblock-deserializer-static-vs-conversational.md for why this dispatch works.
@Component
@RequiredArgsConstructor
public class BlockFactory {

    private final ObjectMapper objectMapper;

    public AbstractBlock build(AiBlockDto dto) {
        if (dto instanceof AiStaticBlockDto staticDto) {
            return buildStatic(staticDto);
        }
        if (dto instanceof AiConversationalBlockDto conversationalDto) {
            return buildConversational(conversationalDto);
        }
        throw new IllegalArgumentException("Unsupported AiBlockDto: " + dto.getClass());
    }

    private AbstractBlock buildStatic(AiStaticBlockDto dto) {
        Map<String, Object> merged = new HashMap<>(dto.getAdditionalProperties());
        merged.put("type", "STATIC");
        merged.put("staticType", dto.getStaticType());
        merged.put("label", dto.getLabel());
        merged.put("required", dto.isRequired());

        return objectMapper.convertValue(merged, AbstractBlock.class);
    }

    private AbstractBlock buildConversational(AiConversationalBlockDto dto) {
        Map<String, Object> merged = new HashMap<>();
        merged.put("type", "CONVERSATIONAL");
        merged.put("label", dto.getLabel());
        merged.put("required", dto.isRequired());
        merged.put("prompt", dto.getPrompt());
        merged.put("persona", dto.getPersona());
        merged.put("maxQuestions", dto.getMaxQuestions());

        return objectMapper.convertValue(merged, AbstractBlock.class);
    }
}
