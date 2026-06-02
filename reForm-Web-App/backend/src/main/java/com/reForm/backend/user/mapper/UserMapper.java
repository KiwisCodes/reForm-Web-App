package com.reForm.backend.user.mapper;

import com.reForm.backend.user.dto.UserResponseDto;
import com.reForm.backend.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponseDto toUserResponseDto(User user);
}
