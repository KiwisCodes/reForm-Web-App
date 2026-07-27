package com.reForm.backend.form.entity.block;

import com.reForm.backend.form.entity.block.conversationalBlock.ConversationalBlock;
import com.reForm.backend.form.entity.block.staticblock.StaticBlock;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

// Branches once on "type" (STATIC vs CONVERSATIONAL — previously an inert field, ignored by
// dispatch), then makes a fresh, independent top-level call into the resolved abstract subclass.
// That second call is NOT a nested/chained resolution from this deserializer's own dispatch — as
// far as Jackson is concerned it's the same kind of call as objectMapper.readValue(json,
// StaticBlock.class) from outside code, so StaticBlock's own "staticType" @JsonTypeInfo resolves
// its 11 leaves normally. Verified empirically before relying on it (see week3 knowledge docs).
public class AbstractBlockDeserializer extends StdDeserializer<AbstractBlock> {

    public AbstractBlockDeserializer() {
        super(AbstractBlock.class);
    }

    @Override
    public AbstractBlock deserialize(JsonParser p, DeserializationContext ctxt) {
        JsonNode node = ctxt.readTree(p);
        String type = node.path("type").asString(null);

        if ("CONVERSATIONAL".equals(type)) {
            return ctxt.readTreeAsValue(node, ConversationalBlock.class);
        }
        return ctxt.readTreeAsValue(node, StaticBlock.class);
    }
}
