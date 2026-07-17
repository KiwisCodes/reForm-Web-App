package com.reForm.backend.form.entity.block.staticblock;

import com.reForm.backend.form.entity.block.AbstractBlock;
import com.reForm.backend.form.entity.block.BlockType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Type resolution for concrete blocks (SHORT_TEXT, LONG_TEXT, etc.) now lives on AbstractBlock —
// see the comment there for why a JsonTypeInfo/JsonSubTypes pair at this level doesn't work.

@Getter
@Setter
@NoArgsConstructor

public abstract class StaticBlock extends AbstractBlock {

    private String placeholder;


    @Override
    public BlockType getType() {
        return BlockType.STATIC;
    }

}
