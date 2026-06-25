package com.reForm.backend.submission.mapper;

import com.reForm.backend.submission.dto.SubmissionResponseDto;
import com.reForm.backend.submission.entity.Submission;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")

public interface SubmissionMapper {

    SubmissionResponseDto toResponseDto(Submission submission);

}
