package com.reForm.backend.auth.service;

import com.reForm.backend.auth.dto.AuthResponseDto;
import com.reForm.backend.auth.dto.LoginRequestDto;
import com.reForm.backend.auth.port.IAuthService;
import com.reForm.backend.auth.port.ITokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private final AuthenticationManager authenticationManager;
    private final ITokenProvider tokenProvider;

    @Override
    public AuthResponseDto login(LoginRequestDto loginRequestDto) {
        log.info("Attempting login verification for email: {}", loginRequestDto.username());

        // 1. Wrap raw credentials inside an unauthenticated Spring token
        UsernamePasswordAuthenticationToken unauthenticatedToken = new UsernamePasswordAuthenticationToken(
                loginRequestDto.username().toLowerCase(), // Normalize input
                loginRequestDto.password()
        );

        // 2. Delegate credential checking to the AuthenticationManager
        // This invokes DaoAuthenticationProvider, CustomUserDetailsService, and PasswordEncoder internally
        Authentication authenticatedResult = authenticationManager.authenticate(unauthenticatedToken);

        // 3. Extract the authenticated Principal details from the result
        CustomerUserDetails userDetails = (CustomerUserDetails) authenticatedResult.getPrincipal();
        log.info("Credentials matched. Generating secure token for user ID: {}", userDetails.getId());

        // 4. Generate the signed stateless JWT token
        String accessToken = tokenProvider.generateToken(userDetails);

        // 5. Extract role information for response metadata mapping
        String role = userDetails
                .getAuthorities()
                .iterator()
                .next()
                .getAuthority();

        return new AuthResponseDto(
                accessToken,
                userDetails.getId(),
                userDetails.getUsername(), // In our adapter, getUsername() returns email
                role
        );
    }
}