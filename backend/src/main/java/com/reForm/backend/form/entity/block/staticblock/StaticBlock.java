package com.reForm.backend.form.entity.block.staticblock;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.reForm.backend.form.entity.block.AbstractBlock;
import com.reForm.backend.form.entity.block.BlockType;
import com.reForm.backend.form.entity.block.staticblock.complex.DateTimeStaticBlock;
import com.reForm.backend.form.entity.block.staticblock.quantitative.NumberRatingStaticBlock;
import com.reForm.backend.form.entity.block.staticblock.quantitative.OpinionScaleStaticBlock;
import com.reForm.backend.form.entity.block.staticblock.quantitative.StarRatingStaticBlock;
import com.reForm.backend.form.entity.block.staticblock.selection.ChoiceStaticBlock;
import com.reForm.backend.form.entity.block.staticblock.standard.*;
import com.reForm.backend.form.entity.block.staticblock.upload.FileUploadStaticBlock;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "staticType"
)
@JsonSubTypes({
        // Standard Inputs
        @JsonSubTypes.Type(value = ShortTextStaticBlock.class, name = "SHORT_TEXT"),
        @JsonSubTypes.Type(value = LongTextStaticBlock.class, name = "LONG_TEXT"),
        @JsonSubTypes.Type(value = EmailStaticBlock.class, name = "EMAIL"),
        @JsonSubTypes.Type(value = PhoneStaticBlock.class, name = "PHONE"),
        @JsonSubTypes.Type(value = UrlStaticBlock.class, name = "URL"),

        // Selection Inputs
        @JsonSubTypes.Type(value = ChoiceStaticBlock.class, name = "CHOICE"),

        // Quantitative Inputs
        @JsonSubTypes.Type(value = StarRatingStaticBlock.class, name = "RATING_STARS"),
        @JsonSubTypes.Type(value = NumberRatingStaticBlock.class, name = "RATING_NUMBERS"),
        @JsonSubTypes.Type(value = OpinionScaleStaticBlock.class, name = "OPINION_SCALE"),

        // Complex Inputs
        @JsonSubTypes.Type(value = DateTimeStaticBlock.class, name = "DATE_TIME"),

        // File & Media Uploads
        @JsonSubTypes.Type(value = FileUploadStaticBlock.class, name = "FILE_UPLOAD")
})

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
