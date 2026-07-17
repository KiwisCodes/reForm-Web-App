package com.reForm.backend.form.entity.block;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.reForm.backend.form.entity.block.staticblock.complex.DateTimeStaticBlock;
import com.reForm.backend.form.entity.block.staticblock.quantitative.NumberRatingStaticBlock;
import com.reForm.backend.form.entity.block.staticblock.quantitative.OpinionScaleStaticBlock;
import com.reForm.backend.form.entity.block.staticblock.quantitative.StarRatingStaticBlock;
import com.reForm.backend.form.entity.block.staticblock.selection.ChoiceStaticBlock;
import com.reForm.backend.form.entity.block.staticblock.standard.*;
import com.reForm.backend.form.entity.block.staticblock.upload.FileUploadStaticBlock;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Jackson can't cascade a nested @JsonTypeInfo (StaticBlock's own "staticType" discriminator
// only applies once you're already deserializing as StaticBlock, which never happens since
// polymorphic dispatch stops at the first concrete match). So the leaf types are listed here
// directly, keyed by "staticType", instead of routing through the abstract StaticBlock class.
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "staticType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ShortTextStaticBlock.class, name = "SHORT_TEXT"),
        @JsonSubTypes.Type(value = LongTextStaticBlock.class, name = "LONG_TEXT"),
        @JsonSubTypes.Type(value = EmailStaticBlock.class, name = "EMAIL"),
        @JsonSubTypes.Type(value = PhoneStaticBlock.class, name = "PHONE"),
        @JsonSubTypes.Type(value = UrlStaticBlock.class, name = "URL"),
        @JsonSubTypes.Type(value = ChoiceStaticBlock.class, name = "CHOICE"),
        @JsonSubTypes.Type(value = StarRatingStaticBlock.class, name = "RATING_STARS"),
        @JsonSubTypes.Type(value = NumberRatingStaticBlock.class, name = "RATING_NUMBERS"),
        @JsonSubTypes.Type(value = OpinionScaleStaticBlock.class, name = "OPINION_SCALE"),
        @JsonSubTypes.Type(value = DateTimeStaticBlock.class, name = "DATE_TIME"),
        @JsonSubTypes.Type(value = FileUploadStaticBlock.class, name = "FILE_UPLOAD")
})
// "type" (e.g. "STATIC") is still sent by clients as the block category, but it no longer
// drives Jackson dispatch, so treat it as inert rather than failing on an unmapped property.
@JsonIgnoreProperties(ignoreUnknown = true)

@Getter
@Setter

public abstract class AbstractBlock implements IFormBlock{

    private UUID id = UUID.randomUUID();
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
