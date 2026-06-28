package com.reForm.backend.user.port;

import com.reForm.backend.user.dto.UserRegisterRequestDto;
import com.reForm.backend.user.dto.UserResponseDto;
import com.reForm.backend.user.dto.UserUpdateRequestDto;

import java.util.UUID;

public interface IUserService {
    public UserResponseDto registerUser(UserRegisterRequestDto userRegisterRequestDto);
    public UserResponseDto updateUser(UUID uuid, UserUpdateRequestDto userUpdateRequestDto);
//    public UserResponseDto updatePassword(UserUpdateRequestDto userUpdateRequestDto);
    public UserResponseDto getUserProfile(UUID uuid);
    public void deleteUser(UUID uuid);
}
