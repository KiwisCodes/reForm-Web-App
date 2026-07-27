package com.reForm.backend.form.entity.block.conversationalBlock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.reForm.backend.form.entity.block.AbstractBlock;
import com.reForm.backend.form.entity.block.BlockType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Reached via AbstractBlockDeserializer's fresh top-level call once "type" == "CONVERSATIONAL".
// "type" itself isn't a field here, hence ignoreUnknown — same reason StaticBlock needs it too.
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
public class ConversationalBlock extends AbstractBlock {

    private String prompt;
    private String persona;
    private Integer maxQuestions;

    @Override
    public BlockType getType() {
        return BlockType.CONVERSATIONAL;
    }
}
