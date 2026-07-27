package com.reForm.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class AiStaticBlockDto extends AiBlockDto {

    private String staticType;

    private final Map<String, Object> additionalProperties = new HashMap<>();

    @JsonAnySetter // dynamically adding unmapped properties to a mutable map during deserialization
    public void addAdditionalProperty(String key, Object value) {
        this.additionalProperties.put(key, value);
    }
}
