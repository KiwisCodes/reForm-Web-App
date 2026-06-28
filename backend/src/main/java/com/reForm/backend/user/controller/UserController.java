package com.reForm.backend.user.controller;


import com.reForm.backend.user.dto.UserRegisterRequestDto;
import com.reForm.backend.user.dto.UserResponseDto;
import com.reForm.backend.user.dto.UserUpdateRequestDto;
import com.reForm.backend.user.port.IUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:3000}") //use the link in the applicaiton.yml, if not, fall back to local host
@Slf4j
@Validated
public class UserController {
    private final IUserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> getUser(@PathVariable UUID id){
        log.info("GET request to retrieve user with id: {}", id);
        UserResponseDto userResponseDto = userService.getUserProfile(id);
        return ResponseEntity.status(HttpStatus.OK).body(userResponseDto); // 200 OK
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponseDto> registerUser(@RequestBody @Valid UserRegisterRequestDto userRegisterRequestDto){
        log.info("POST request to register user: {}", userRegisterRequestDto);
        UserResponseDto userResponseDto = userService.registerUser(userRegisterRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(userResponseDto); //this return 201
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UserResponseDto> updateUser(@PathVariable UUID id, @RequestBody @Valid UserUpdateRequestDto userUpdateRequestDto){
        log.info("PATCH request to update user with id: {}", id);
        UserResponseDto userResponseDto = userService.updateUser(id, userUpdateRequestDto);
        return ResponseEntity.status(HttpStatus.OK).body(userResponseDto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id){
        log.info("DELETE request to delete user with id: {}", id);
        userService.deleteUser(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
