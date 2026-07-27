package com.reForm.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "category"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AiStaticBlockDto.class, name = "STATIC"),
        @JsonSubTypes.Type(value = AiConversationalBlockDto.class, name = "CONVERSATIONAL")
})
// "type" (e.g. "STATIC") is still sent by clients as the block category, but it no longer
// drives Jackson dispatch, so treat it as inert rather than failing on an unmapped property.
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public abstract class AiBlockDto {

    // Shared by every block category: a static field can be skippable, and so can an AI-asked
    // question — the user can decline to answer it.
    private String label;
    private boolean required;
}
