package com.reForm.backend.form.entity.block;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.cglib.core.Block;

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

    private StaticBlockType subType;

    private String placeholder;


    @Override
    public BlockType getType() {
        return BlockType.STATIC;
    }

}
