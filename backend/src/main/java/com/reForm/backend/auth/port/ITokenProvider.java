package com.reForm.backend.auth.port;

import com.reForm.backend.auth.service.CustomerUserDetails;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

public interface ITokenProvider {
    public String generateToken(UserDetails userDetails);
    boolean validateToken(String token);
    public UUID extractUserId(String token);
    public String extractEmail(String token);
    public String extractRole(String token);

}
