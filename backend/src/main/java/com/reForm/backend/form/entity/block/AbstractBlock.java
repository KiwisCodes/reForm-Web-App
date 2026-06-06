package com.reForm.backend.form.entity.block;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
@JsonSubTypes.Type(value = StaticBlock.class, name = "STATIC")
})

@Getter
@Setter

public abstract class AbstractBlock implements IFormBlock{

    private UUID id = UUID.randomUUID();;
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
