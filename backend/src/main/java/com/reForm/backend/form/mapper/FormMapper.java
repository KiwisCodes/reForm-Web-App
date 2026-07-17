package com.reForm.backend.form.mapper;

import com.reForm.backend.form.dto.*;
import com.reForm.backend.form.entity.Form;
import com.reForm.backend.form.entity.block.AbstractBlock;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

// Add block mapper
@Mapper(componentModel = "spring")
public interface FormMapper {
    FormResponseDto toResponseDto(Form form);

    List<FormResponseDto> toResponseDtoList(List<Form> forms);

    PublicFormResponseDto toPublicFormResponseDto(Form form);

    @Mapping(source = "rules", target = "rules")
    FormSubmissionDto toFormSubmissionDto(Form form, List<BlockValidationRule> rules);

    List<BlockValidationRule> toBlockValidationRule(List<AbstractBlock> blocks);

}
