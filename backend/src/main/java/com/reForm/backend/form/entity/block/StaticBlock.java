package com.reForm.backend.form.entity.block;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

enum StaticBlockType {
    NAME,
    EMAIL,
    TEXT
}

@Getter
@Setter
@NoArgsConstructor

public class StaticBlock extends AbstractBlock{

    @Enumerated(EnumType.STRING)
    private StaticBlockType subType;

    private String placeholder;


    @Override
    public String getType() {
        return "STATIC";
    }

}
