package com.reForm.backend.submission.mapper;

import com.reForm.backend.submission.dto.SubmissionResponseDto;
import com.reForm.backend.submission.entity.Submission;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")

public interface SubmissionMapper {

    @Mapping(target = "message", constant = "Submission was successfully saved!")
    SubmissionResponseDto toResponseDto(Submission submission);

}
