package com.reForm.backend.ai.parser;

import com.reForm.backend.ai.dto.AiBlockDto;
import com.reForm.backend.ai.dto.AiFormDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

// Turns Gemini's raw JSON response text into typed DTOs — the one piece the processing chain was
// missing; everything downstream (FormFactory, BlockFactory, AiBlockApplicationService) already
// assumed this had already happened. Kept intentionally minimal: assumes well-formed JSON, since
// the plan is to use Gemini's structured-output/responseSchema mode (see BlockSchemaGenerator),
// which doesn't wrap output in markdown fences or preamble text the way a plain conversational
// request can. If real Gemini integration later shows messier output than that, this is where
// repair/retry logic would go — not speculatively added now.
//
// Deliberately not wired into AiBlockApplicationService — stays a standalone step so the boundary
// between "untrusted raw text in" and "typed object in" stays where it was drawn (see week3
// knowledge docs for why that split was chosen over a method on BlockFactory).
@Component
@RequiredArgsConstructor
public class AiResponseParser {

    private final ObjectMapper objectMapper;

    public AiFormDto parseForm(String rawJson) {
        return objectMapper.readValue(rawJson, AiFormDto.class);
    }

    public List<AiBlockDto> parseBlocks(String rawJson) {
        return objectMapper.readValue(rawJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, AiBlockDto.class));
    }
}
