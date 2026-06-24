package com.reForm.backend.auth.port;

import com.reForm.backend.auth.service.CustomerUserDetails;

import java.util.UUID;

public interface ITokenProvider {
    public String generateToken(CustomerUserDetails customerUserDetail);
    boolean validateToken(String token);
    public UUID extractUserId(String token);

}
