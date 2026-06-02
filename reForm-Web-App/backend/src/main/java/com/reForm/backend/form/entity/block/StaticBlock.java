package com.reForm.backend.form.entity.block;

import jakarta.persistence.Enumerated;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

enum StaticBlockType {
    TEXT,
    EMAIL,
    NUMBER
}

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class StaticBlock extends AbstractBlock{

    private String placeholder;

    @Enumerated
    private StaticBlockType subType;

    @Override
    public String getType() {
        return "STATIC";
    }
}
