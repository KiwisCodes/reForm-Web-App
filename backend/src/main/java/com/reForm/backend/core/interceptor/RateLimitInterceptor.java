package com.reForm.backend.core.interceptor;

import com.reForm.backend.core.port.IRateLimitService;
import com.reForm.backend.user.entity.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {
    private final IRateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // clientKey: The raw unique identifier of the client.
        //   - For guests: the IP address (e.g. "203.0.113.195").
        //   - For logged-in users: their unique database User UUID (e.g. "usr_abc123").
        String clientKey = resolveClientKey(request);
        
        // role: The user's role (FORM_BUILDER, FORM_FILLER, ADMIN, or null for guests).
        Role role = resolveUserRole();

        // allowed: Queries the service which checks the matching redisKey in the database.
        // ---------------------------------------------------------------------------------
        // REDIS KEY COMPARISON:
        //   - clientKey (In-App): Used in interceptor and service to identify a request.
        //   - redisKey (In-Database): Built in the service by combining role prefix + clientKey.
        //     Format: "rate-limit:ROLE:clientKey"
        //     Examples:
        //       - Guest: "rate-limit:ANONYMOUS:203.0.113.195"
        //       - Member: "rate-limit:FORM_FILLER:usr_abc123"
        // ---------------------------------------------------------------------------------
        boolean allowed = rateLimitService.tryConsume(clientKey, role);
        if(allowed) {
            return true; // Token available: Proceed to the Controller
        }

        // Limit exceeded: Calculate backoff wait time
        long waitTime = rateLimitService.getWaitTimeInSeconds(clientKey, role);
        log.warn("Blocking request from client: [{}] (Role: {}). Rate limit exceeded. Backoff: {}s", clientKey, role, waitTime);

        buildBlockResponse(response, waitTime);
        return false; // Blocks request pipeline execution
    }

    private String resolveClientKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // DIFFERENCE BETWEEN getPrincipal() AND getName():
        //   - authentication.getPrincipal(): Returns the UserDetails object containing user details.
        //     If not logged in, it returns a plain String "anonymousUser".
        //   - authentication.getName(): Returns the unique string identity (e.g., database UUID/Username).
        //
        // We verify authentication is not null, is authenticated, and the principal object is not 
        // the default "anonymousUser" String, confirming a real user is logged in.
        if(authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return authentication.getName(); // Returns the unique user database UUID
        }

        // HTTP HEADER vs. BODY EXPLANATION:
        //   - HTTP Header: Metadata sent as key-value pairs at the top of the HTTP packet.
        //     Example: "Authorization: Bearer <token>", "X-Forwarded-For: 203.0.113.195"
        //   - HTTP Body: The actual data payload carried at the bottom of the HTTP packet.
        //     Example: JSON string {"workspaceName": "HR Dept"}
        //
        // WHAT IS X-Forwarded-For:
        //   In production, requests travel through reverse proxies (e.g., Nginx, Cloudflare) 
        //   before hitting Spring Boot. request.getRemoteAddr() would return the Nginx server's IP, 
        //   causing all users in the world to share the same rate-limit bucket!
        //   "X-Forwarded-For" is an HTTP header injected by proxies to preserve the client's real IP.
        String ipAddress = request.getHeader("X-FORWARDED-FOR");
        if(ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr(); // Fallback to remote socket IP if no proxy is present
        }

        // MULTIPLE PROXIES & JAVA SPLIT EXPLANATION:
        //   If a request passes through multiple hops (User -> Cloudflare -> Nginx -> Tomcat), 
        //   each proxy appends its forwarding IP to the X-Forwarded-For header, resulting in a comma-separated list:
        //   "client_ip, proxy1_ip, proxy2_ip" (e.g., "203.0.113.195, 172.67.114.1, 10.0.0.5").
        //   The real client IP is always the FIRST element in the list.
        //
        // WHY ipAddress.split(",")[0] AND NOT ipAddress[0].split(","):
        //   - ipAddress is a java.lang.String object. You cannot use brackets index "[0]" directly on a String.
        //   - ipAddress.split(",") splits the string into a String[] array. 
        //   - Example: If the header is "203.0.113.195, 172.67.114.1", split(",") returns the array:
        //     ["203.0.113.195", " 172.67.114.1"]
        //   - By appending "[0]", we retrieve the first element of that array: "203.0.113.195"
        //   - Finally, we call .trim() to clean any leading/trailing spaces.
        if(ipAddress != null && ipAddress.contains(",")){
            ipAddress = ipAddress.split(",")[0].trim();
            return ipAddress;
        }

        return ipAddress;
    }

    private Role resolveUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication != null
        && authentication.isAuthenticated()
            && !"anonymousUser".equals(authentication.getPrincipal())){
            return authentication
                    .getAuthorities()
                    .stream()
                    .map(grantedAuthority -> {
                        try{
                            String roleName = grantedAuthority
                                    .getAuthority()
                                    .replace("ROLE_", "");
                            return Role.valueOf(roleName);
                        } catch (IllegalArgumentException e){
                            return Role.FORM_FILLER;
                        }
                    })
                    .findFirst()
                    .orElse(Role.FORM_FILLER);
        }
        return null; // Returns null for unauthenticated guests (mapped to ANONYMOUS configuration limits)
    }

    private void buildBlockResponse(HttpServletResponse response, long waitTime) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value()); // HTTP 429 status code
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        
        // Tells the client how many seconds they must wait before making another request
        response.setHeader("Retry-After", String.valueOf(waitTime));

        Map<String, Object> errorsDetails = Map.of(
                "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                "error", "Too Many Requests",
                "message", "Rate limit exceeded. Please try again after the backoff duration.",
                "retryAfterSeconds", waitTime
        );
        response.getWriter().write(objectMapper.writeValueAsString(errorsDetails));
    }
}
