package com.reForm.backend.auth.service;

import com.reForm.backend.auth.port.ITokenProvider;
import org.springframework.stereotype.Component;

import java.util.UUID;


/*
@Component is the semantically correct stereotype for technical utilities, cryptographic adapters,
and infrastructure components (like token providers, converters, or file storage adapters).
 */
@Component
public class JwtTokenImpl implements ITokenProvider {
    @Override
    public String generateToken(CustomerUserDetails customerUserDetail) {
        return "";
    }

    @Override
    public boolean validateToken(String token) {
        return false;
    }

    @Override
    public UUID extractUserId(String token) {
        return null;
    }
}
