package com.reForm.backend.form.entity.block;

import tools.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Dispatch is now a custom deserializer (AbstractBlockDeserializer) instead of a flat
// @JsonTypeInfo/@JsonSubTypes list: it branches once on "type" (STATIC vs CONVERSATIONAL), then
// makes a fresh top-level call into StaticBlock.class or ConversationalBlock.class. That second
// call re-enters ordinary polymorphic resolution (StaticBlock keeps its own "staticType"
// @JsonTypeInfo/@JsonSubTypes for its 11 leaves) — verified empirically, since Jackson does NOT
// automatically cascade a nested @JsonTypeInfo when the outer dispatch is annotation-driven
// (that's the trap a single shared "staticType" discriminator on this class used to avoid).
@JsonDeserialize(using = AbstractBlockDeserializer.class)
@Getter
@Setter
public abstract class AbstractBlock implements IFormBlock {

    private UUID id = UUID.randomUUID();
    private String label;
    private String description;
    private boolean isRequired;
    private Integer sortOrder;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public Integer getSortOrder() {
        return sortOrder;
    }
}
