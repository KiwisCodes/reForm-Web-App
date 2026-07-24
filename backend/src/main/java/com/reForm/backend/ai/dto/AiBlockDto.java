package com.reForm.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;


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

public interface AiBlockDto {

}
