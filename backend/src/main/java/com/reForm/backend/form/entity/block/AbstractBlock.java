package com.reForm.backend.form.entity.block;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Dispatch is a custom deserializer (AbstractBlockDeserializer) instead of a flat
// @JsonTypeInfo/@JsonSubTypes list: it branches once on "type" (STATIC vs CONVERSATIONAL), then
// makes a fresh top-level call into StaticBlock.class or ConversationalBlock.class. That second
// call re-enters ordinary polymorphic resolution (StaticBlock keeps its own "staticType"
// @JsonTypeInfo/@JsonSubTypes for its 11 leaves) — verified empirically, since Jackson does NOT
// automatically cascade a nested @JsonTypeInfo when the outer dispatch is annotation-driven
// (that's the trap a single shared "staticType" discriminator on this class used to avoid).
//
// Registered via a SimpleModule bean (see AbstractBlockJacksonConfig), NOT @JsonDeserialize on
// this class — a class-level @JsonDeserialize is inherited by subclasses in Jackson's annotation
// introspection, which made StaticBlock (and every leaf) also resolve through
// AbstractBlockDeserializer instead of their own bean/@JsonTypeInfo logic, breaking the "fresh
// top-level call" the whole design depends on. Module registration binds to the exact declared
// class only and does not cascade — confirmed the hard way via FormFactoryTest.
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
