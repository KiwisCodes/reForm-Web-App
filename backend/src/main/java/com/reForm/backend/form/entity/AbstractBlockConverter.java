package com.reForm.backend.form.entity;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import com.reForm.backend.form.entity.block.AbstractBlock;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.List;
@Converter
public class AbstractBlockConverter implements AttributeConverter<List<AbstractBlock>, String> {
    private final ObjectMapper objectMapper = new ObjectMapper(); // configured with proper modules

    // writeValueAsString(Object) erases the List's generic element type at runtime, so Jackson
    // treats each element as its own root type and never applies AbstractBlock's polymorphic
    // @JsonTypeInfo (the "staticType" discriminator ends up missing). Passing the JavaType
    // explicitly restores that context.
    private final JavaType blockListType = objectMapper.getTypeFactory()
            .constructCollectionType(List.class, AbstractBlock.class);

    @Override
    public String convertToDatabaseColumn(List<AbstractBlock> attribute) {
        return objectMapper.writerFor(blockListType).writeValueAsString(attribute);
    }

    @Override
    public List<AbstractBlock> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return new ArrayList<>();
        }
        return objectMapper.readValue(dbData, blockListType);
    }
}