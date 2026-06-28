package com.reForm.backend.auth.port;

import com.reForm.backend.auth.dto.AuthResponseDto;
import com.reForm.backend.auth.dto.LoginRequestDto;

/**
 * Port defining the contract for security authentication operations.
 */
public interface IAuthService {

    /**
     * Authenticates login credentials and generates a signed JWT token.
     *
     * @param loginRequestDto the incoming user credentials (email and password)
     * @return the standard authentication response payload containing the token and metadata
     */
    AuthResponseDto login(LoginRequestDto loginRequestDto);
}