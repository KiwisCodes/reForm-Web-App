package com.reForm.backend.auth.config;

// Custom security filters and exception handlers built within your application
import com.reForm.backend.auth.filter.JwtAuthenticationFilter;
import com.reForm.backend.auth.exception.CustomAuthenticationEntryPoint;
import com.reForm.backend.auth.exception.CustomAccessDeniedHandler;

// Lombok annotation that auto-generates a constructor for any fields marked 'final'
import com.reForm.backend.auth.port.ITokenProvider;
import lombok.RequiredArgsConstructor;

// Core Spring framework configuration annotations
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Spring Security management dependencies
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Cross-Origin Resource Sharing (CORS) security configuration modules
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Central security gatekeeper configuration for the application.
 * Establishes stateless JWT session management, CORS mapping, and path permission controls.
 */
@Configuration // Specifies that this class contains factory methods (@Bean) to initialize core infrastructure setups.
@EnableWebSecurity // Instructs Spring to bypass its default basic security setup and execute this custom pipeline instead.
@RequiredArgsConstructor // Automatically hooks up Dependency Injection for all final variables during startup.
@EnableMethodSecurity // A BUG WITHOUT THIS, WHAT IS IT THOUGH
/*
Bug 2 : Missing Method Security Enablement (The Silent Bypass)
The Problem:
Once you fix Bug 1 , your authentication will succeed.
However, your @PreAuthorize guards on your WorkspaceController (e.g., @PreAuthorize("@workspaceSecurity.isMember(...)"))
will not execute. Anyone who is logged in can query or delete any workspace UUID because the security check is bypassed.
Why It Happens:
Spring Security does not scan controllers for @PreAuthorize annotations out of the box.
You must explicitly configure Spring to wrap your controllers in security proxy aspects.
How to Fix It:
Add @EnableMethodSecurity to your main SecurityConfig.java class:
 */
public class SecurityConfig {

    // FIX (Bug 1 - Double Registration):
    // We no longer inject JwtAuthenticationFilter as a constructor field.
    // If we did, Spring would try to find/build the 'jwtAuthenticationFilter' bean to inject here,
    // but that bean is defined by the @Bean method below — which lives inside THIS class.
    // Spring can't finish building SecurityConfig without the filter, and can't build the filter
    // without finishing SecurityConfig first. Deadlock / circular reference.
    //
    // Instead, we inject only the RAW DEPENDENCIES the filter needs (ITokenProvider + UserDetailsService),
    // and we construct the filter ourselves inside the @Bean method below.
    // This way SecurityConfig has no dependency on its own output bean.

    // 1. Raw dependency needed to construct JwtAuthenticationFilter
    private final ITokenProvider tokenProvider;

    // 2. Raw dependency needed to construct JwtAuthenticationFilter
    private final UserDetailsService userDetailsService;

    // 3. Injected custom HTTP 401 error translator (Triggers when NO token or an EXPIRED token is supplied).
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

    // 4. Injected custom HTTP 403 error translator (Triggers when logged-in users try to access unauthorized roles).
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    // FIX (Bug 1 - Double Registration):
    // We manually declare the filter as a @Bean here instead of annotating JwtAuthenticationFilter with @Component.
    // @Component would cause Tomcat to auto-register it as a global Servlet filter AND Spring Security would
    // also register it via .addFilterBefore(...) below — running it twice, with SecurityContextHolderFilter
    // wiping the authentication in between, causing 401s on every protected request.
    //
    // By declaring it here as a @Bean with no @Component on the class itself, it only exists inside the
    // Spring Security filter chain — exactly where we want it.
    //
    // IMPORTANT: When securityFilterChain() calls jwtAuthenticationFilter() below, Spring intercepts that
    // method call and returns the already-created singleton bean — it does NOT construct a second instance.
    // This is a special behaviour of @Bean methods inside @Configuration classes.
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(tokenProvider, userDetailsService);
    }

    /**
     * Configures the step-by-step pipeline (Filter Chain) that every single network request passes through.
     * It tells which filter are active or disabled.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Disable CSRF safely since our tokens are stored statelessly
                // Cross-Site Request Forgery attacks rely on automatic browser session cookies.
                // Because we pass JWT tokens manually inside header strings, our architecture is naturally safe against CSRF.
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Register CORS configuration rules
                // Instructs the underlying engine to use the custom cross-origin rules declared further down in this file.
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 3. Configure HTTP route permissions
                // Sets up explicit access controls. Requests are evaluated in top-to-bottom order.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()                // Open login and authentication paths (No token needed)
                        .requestMatchers("/api/v1/users/register").permitAll()          // Open user registration path (No token needed)
                        .anyRequest().authenticated()                                   // Secure all other endpoints (A valid JWT MUST be provided)
                )

                // 4. Force Spring to keep sessions stateless (No HTTP Sessions created)
                // Prevents the backend server from generating JSESSIONID cookies or consuming RAM to track logged-in users.
                // Every request is treated as a fresh, separate entity that must present its own credentials.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 5. Override default exception response entry points (JSON outputs)
                // Registers our custom handlers to catch lifecycle runtime security crashes and format them cleanly.
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(customAuthenticationEntryPoint) // Handles 401 Unauthorized errors
                        .accessDeniedHandler(customAccessDeniedHandler)           // Handles 403 Forbidden errors
                )

                // 6. Mount our custom JWT filter before Spring's native Authentication filters
                // Intercepts the request right before UsernamePasswordAuthenticationFilter runs.
                // This extracts the JWT token from the headers and tells Spring who the user is before security rules are evaluated.
                // We call jwtAuthenticationFilter() as a method here — Spring returns the singleton @Bean, not a new instance.
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Defines permissions letting external browser environments securely stream requests to this API backend.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Define origins allowed to execute cross-origin requests targeting our API
        // Tells web browsers that it is perfectly safe to let our Next.js frontend code read the data returned by this server.
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));

        // Authorizes specific HTTP methods. Browsers send an automatic pre-flight "OPTIONS" check first before executing data modifications.
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Tells browsers which headers are allowed to pass through cross-origin streams.
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Cache-Control")); //what is cache control?

        // Exposes headers and cookie values safely across origins. Required if you pass cookies or secure session tokens.
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Maps these specific cross-origin allowance rules across every single endpoint route mapped in our application ("/**").
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Exposes the built-in Spring authentication manager bean to handle raw credential checks if needed elsewhere.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        // Exposes Spring Security's native credential validation manager for REST services to use
        return configuration.getAuthenticationManager();
    }
}