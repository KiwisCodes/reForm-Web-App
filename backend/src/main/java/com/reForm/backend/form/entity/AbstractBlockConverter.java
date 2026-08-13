package com.reForm.backend.form.entity;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import com.reForm.backend.form.entity.block.AbstractBlock;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
@Converter
@Component
public class AbstractBlockConverter implements AttributeConverter<List<AbstractBlock>, String> {
    private final ObjectMapper objectMapper;
    private final JavaType blockListType;

    public AbstractBlockConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.blockListType = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, AbstractBlock.class);
    }

    @Override
    public String convertToDatabaseColumn(List<AbstractBlock> attribute) {
        try {
            if (attribute == null) {
                return null;
            }
            // Serializing the whole list at once (even with blockListType passed explicitly)
            // pins Jackson's per-element type lookup to AbstractBlock, the collection's static
            // element type. AbstractBlock itself carries no @JsonTypeInfo (only StaticBlock, one
            // level down, does), so the "staticType" discriminator never gets written and the
            // round trip back through AbstractBlockDeserializer -> StaticBlock fails. Converting
            // each block individually makes every element its own serialization root, so Jackson
            // resolves the TypeSerializer from the block's actual runtime class instead — verified
            // empirically (see AbstractBlockConverterScratchTest).
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (AbstractBlock block : attribute) {
                arrayNode.add(objectMapper.valueToTree(block));
            }
            return objectMapper.writeValueAsString(arrayNode);
        } catch (JacksonException e) {
            throw new RuntimeException("Error writing blocks to JSON string", e);
        }
    }

    @Override
    public List<AbstractBlock> convertToEntityAttribute(String dbData) {
        try {
            if (dbData == null || dbData.isEmpty()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(dbData, blockListType);
        } catch (JacksonException e) {
            throw new RuntimeException("Error reading blocks from JSON string", e);
        }
    }
}