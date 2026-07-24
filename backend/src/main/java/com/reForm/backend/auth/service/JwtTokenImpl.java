package com.reForm.backend.auth.service;

import com.reForm.backend.auth.port.ITokenProvider;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;
import java.util.UUID;


/*
@Component is the semantically correct stereotype for technical utilities, cryptographic adapters,
and infrastructure components (like token providers, converters, or file storage adapters).
 */
@Component
@Slf4j
public class JwtTokenImpl implements ITokenProvider {
//    @Value("${app.security.secret}")
    private final String jwtSecret;

//    @Value("${app.security.expiration-ms}")
    private final Long expirationMs;

    public JwtTokenImpl(@Value("${app.security.secret}") String jwtSecret,
                        @Value("${app.security.expiration-ms}") Long expirationMs) {
        this.jwtSecret = jwtSecret;
        this.expirationMs = expirationMs;
    }

    private SecretKey getSigningKey() {
        //base64 or base64url, related to + and / replacement
        return Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(jwtSecret));
    }


    @Override
    public String generateToken(UserDetails userDetail) {
        CustomerUserDetails customerUserDetails = (CustomerUserDetails) userDetail;
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(customerUserDetails.getId().toString())
                .claim("email", customerUserDetails.getUsername())
                .claim("role", customerUserDetails
                        .getAuthorities()
                        .iterator()
                        .next()
                        .getAuthority())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    @Override
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e){
            log.error("JWT validation failure: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public UUID extractUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return UUID.fromString(claims.getSubject());
    }

    @Override
    public String extractEmail(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("email", String.class);
    }

    @Override
    public String extractRole(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("role", String.class);
    }
}
