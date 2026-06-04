package com.reForm.backend.form.mapper;

import com.reForm.backend.form.dto.FormCreateDto;
import com.reForm.backend.form.dto.FormResponseDto;
import com.reForm.backend.form.entity.Form;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface FormMapper {
    FormResponseDto toResponseDto(Form form);

    List<FormResponseDto> toResponseDtoList(List<Form> forms);

}
