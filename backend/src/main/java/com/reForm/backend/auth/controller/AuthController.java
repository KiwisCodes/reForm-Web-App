package com.reForm.backend.auth.controller;

import com.reForm.backend.auth.dto.AuthResponseDto;
import com.reForm.backend.auth.dto.LoginRequestDto;
import com.reForm.backend.auth.port.IAuthService;
import com.reForm.backend.user.dto.UserRegisterRequestDto;
import com.reForm.backend.user.dto.UserResponseDto;
import com.reForm.backend.user.port.IUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:3000}")
public class AuthController {

    private final IAuthService authService;
    private final IUserService userService; // Exposes user registration business logic

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@RequestBody @Valid LoginRequestDto loginRequestDto) {
        log.info("Received POST login request for: {}", loginRequestDto.username());
        AuthResponseDto responseDto = authService.login(loginRequestDto);
        return ResponseEntity.status(HttpStatus.OK).body(responseDto);
    }

//    @PostMapping("/register")
//    public ResponseEntity<UserResponseDto> register(@RequestBody @Valid UserRegisterRequestDto registerRequestDto) {
//        log.info("Received POST registration request for: {}", registerRequestDto.email());
//        UserResponseDto responseDto = userService.registerUser(registerRequestDto);
//        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
//    }
}