package com.reForm.backend.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * The Security Filter Chain (The Switchboard).
     * This configures which doors are locked and which are unlocked.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // Disable CSRF for stateless REST APIs
                .cors(cors -> {}) // Enable CORS (inherits our controller @CrossOrigin settings)
                .authorizeHttpRequests(auth -> auth
                        // UNLOCK ALL DOORS FOR LOCAL MVP TESTING!
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}