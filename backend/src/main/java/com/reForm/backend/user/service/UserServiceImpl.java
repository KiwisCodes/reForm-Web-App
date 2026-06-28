package com.reForm.backend.user.service;

import com.reForm.backend.core.exception.ResourceNotFoundException;
import com.reForm.backend.user.dto.UserRegisterRequestDto;
import com.reForm.backend.user.dto.UserResponseDto;
import com.reForm.backend.user.dto.UserUpdateRequestDto;
import com.reForm.backend.user.dto.WorkspaceCreateRequestDto;
import com.reForm.backend.user.entity.Role;
import com.reForm.backend.user.entity.User;
import com.reForm.backend.user.mapper.UserMapper;
import com.reForm.backend.user.port.IUserService;
import com.reForm.backend.user.port.IWorkspaceService;
import com.reForm.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;


@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; //Bean
    private final UserMapper userMapper;
    private final IWorkspaceService workspaceService;


    @Override
    @Transactional
    public UserResponseDto registerUser(UserRegisterRequestDto userRegisterRequestDto) {
        String normalizedEmail = userRegisterRequestDto.email().toLowerCase();
//        if(!userRepository.existsByEmail(normalizedEmail)) {
//            return null;
//        } it is registering, there wont be any in the database
        if(userRepository.existsByEmail(normalizedEmail)) {
//            throw new ResourceNotFoundException("Email address already in use");
            throw new IllegalArgumentException("Email address already in use");
            //why do we do this and not resource not found exception like above?
        }

        User newUser = User.builder()
//                .email(userRegisterRequestDto.email()) this email is not normalized
                .email(normalizedEmail)
                .username(userRegisterRequestDto.username())
                .role(Role.FORM_BUILDER)
                .passwordHash(passwordEncoder.encode(userRegisterRequestDto.password()))
//                .passwordHash("dummy hash")
                .build();
        User savedUser = userRepository.save(newUser);
        WorkspaceCreateRequestDto newWorkspaceCreateRequest = new WorkspaceCreateRequestDto(
                savedUser.getUsername() + " 's Workspace",
                "Default user's workspace"
        );
        workspaceService.createWorkspace(savedUser.getId(), newWorkspaceCreateRequest); //implement later
//        UserResponseDto newUSer = new UserResponseDto()
        return userMapper.toUserResponseDto(savedUser);

    }

    @Override
    @Transactional
    public UserResponseDto updateUser(UUID uuid, UserUpdateRequestDto userUpdateRequestDto) {
        //the uuid is obtained from the security context holder or from the jwt
        User user = userRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + uuid));
        user.setUsername(userUpdateRequestDto.username());
        User savedUpdatedUser = userRepository.save(user);
        return userMapper.toUserResponseDto(savedUpdatedUser);
    }

    @Override
    @Transactional(readOnly = true)
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
