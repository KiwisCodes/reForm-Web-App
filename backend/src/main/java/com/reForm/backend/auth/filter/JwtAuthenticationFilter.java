package com.reForm.backend.auth.filter;

import com.reForm.backend.auth.port.ITokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Custom request interceptor that parses and validates JWT tokens.
 * Executes exactly once per execution thread to maintain high performance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final ITokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = extractBearerToken(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String email = tokenProvider.extractEmail(jwt);

                // Load user from the database via the injected UserDetailsService
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                // Construct our authenticated token representation (credentials are set to null)
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

                // Attach network connection metadata (IP address, session index)
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Place our authenticated user into Spring's thread-local SecurityContextHolder
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.info("Successfully authenticated user: {}", email);
            }
        } catch (Exception e) {
            log.error("Could not set user authentication in security context: {}", e.getMessage());
        }

        // Pass execution control forward to the next sequential filter in the container chain
        filterChain.doFilter(request, response);
    }

    /**
     * Helper method to extract the bearer token from the Authorization HTTP header.
     */
    private String extractBearerToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // Remove "Bearer " prefix to extract token string
        }
        return null;
    }
}