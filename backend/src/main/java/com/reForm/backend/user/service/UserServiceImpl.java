package com.reForm.backend.user.service;

import com.reForm.backend.user.dto.UserLoginRequestDto;
import com.reForm.backend.user.dto.UserRegisterRequestDto;
import com.reForm.backend.user.dto.UserResponseDto;
import com.reForm.backend.user.dto.UserUpdateRequestDto;
import com.reForm.backend.user.mapper.UserMapper;
import com.reForm.backend.user.port.IUserService;
import com.reForm.backend.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;


@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements IUserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;


    @Override
    public UserResponseDto registerUser(UserRegisterRequestDto userRegisterRequestDto) {
        return null;
    }

    @Override
    public UserResponseDto loginUser(UserLoginRequestDto userLoginRequestDto) {
        return null;
    }

    @Override
    public UserResponseDto updateUser(UserUpdateRequestDto userUpdateRequestDto) {
        return null;
    }

    @Override
    public UserResponseDto getUserProfile(UUID uuid) {
        return null;
    }

    @Override
    public void deleteUser(UUID uuid) {

    }
}
