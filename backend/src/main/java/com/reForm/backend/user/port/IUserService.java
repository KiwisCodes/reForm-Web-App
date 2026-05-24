package com.reForm.backend.user.port;

import com.reForm.backend.user.dto.UserLoginRequestDto;
import com.reForm.backend.user.dto.UserRegisterRequestDto;
import com.reForm.backend.user.dto.UserResponseDto;
import com.reForm.backend.user.dto.UserUpdateRequestDto;

import java.util.UUID;

public interface IUserService {
    public UserResponseDto registerUser(UserRegisterRequestDto userRegisterRequestDto);
    public UserResponseDto loginUser(UserLoginRequestDto userLoginRequestDto);
    public UserResponseDto updateUser(UserUpdateRequestDto userUpdateRequestDto);
//    public UserResponseDto updatePassword(UserUpdateRequestDto userUpdateRequestDto);
    public UserResponseDto getUserProfile(UUID uuid);
    public void deleteUser(UUID uuid);
}
