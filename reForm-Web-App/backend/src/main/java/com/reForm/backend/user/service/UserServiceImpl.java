package com.reForm.backend.user.service;

import com.reForm.backend.core.exception.ResourceNotFoundException;
import com.reForm.backend.user.dto.UserLoginRequestDto;
import com.reForm.backend.user.dto.UserRegisterRequestDto;
import com.reForm.backend.user.dto.UserResponseDto;
import com.reForm.backend.user.dto.UserUpdateRequestDto;
import com.reForm.backend.user.entity.Role;
import com.reForm.backend.user.entity.User;
import com.reForm.backend.user.entity.Workspace;
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
public class UserServiceImpl implements IUserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final WorkspaceService workspaceService;


    @Override
    @Transactional
    public UserResponseDto registerUser(UserRegisterRequestDto userRegisterRequestDto) {
        String normalizedEmail = userRegisterRequestDto.email().toLowerCase();
        if(userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email already exists: " + normalizedEmail);
        }
        User newUser = User.builder()
                .email(normalizedEmail)
                .username(userRegisterRequestDto.username())
                .role(Role.FORM_BUILDER)
                .passwordHash(passwordEncoder.encode(userRegisterRequestDto.password()))
                .build();
        User savedUser = userRepository.save(newUser);
        // workspaceService.createDefaultWorkspace(savedUser); // implement in next phase
        return userMapper.toUserResponseDto(savedUser);
    }

    @Override
    public UserResponseDto updateUser(UUID uuid, UserUpdateRequestDto userUpdateRequestDto) {
        //the uuid is obtained from the security context holder or from the jwt
        User user = userRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + uuid));
        user.setUsername(userUpdateRequestDto.username());
        User savedUpdatedUser = userRepository.save(user);
        return userMapper.toUserResponseDto(savedUpdatedUser);
    }

    @Override
    public UserResponseDto getUserProfile(UUID uuid) {
        User user = userRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + uuid));
        return userMapper.toUserResponseDto(user);
    }

    @Override
    @Transactional
    public void deleteUser(UUID uuid) {
        User user = userRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + uuid));
        userRepository.delete(user);
        return;
    }
}
