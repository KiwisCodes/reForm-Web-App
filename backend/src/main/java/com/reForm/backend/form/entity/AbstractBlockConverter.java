package com.reForm.backend.form.entity;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.reForm.backend.form.entity.block.AbstractBlock;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.List;
@Converter
public class AbstractBlockConverter implements AttributeConverter<List<AbstractBlock>, String> {
    private final ObjectMapper objectMapper = new ObjectMapper(); // configured with proper modules
    @Override
    public String convertToDatabaseColumn(List<AbstractBlock> attribute) {
        try {
            return objectMapper.writeValueAsString(attribute);
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
            return objectMapper.readValue(dbData, new TypeReference<List<AbstractBlock>>() {});
        } catch (JacksonException e) {
            throw new RuntimeException("Error reading blocks from JSON string", e);
        }
    }
}