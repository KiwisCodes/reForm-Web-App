# 20: OpenAPI 3.0 & Swagger UI Integration & Architecture Guide

**Author**: Technical Architecture Team  
**Platform**: reForm Enterprise Form Builder & Conversational AI Platform (`com.reForm.backend.config.OpenApiConfig`)  
**Target File**: `backend/knowledge/pth/week4/20_openapi_and_swagger_integration_guide.md`  
**Date**: 2026-08-13  
**Version**: 1.1.0-RELEASE  

---

## Executive Summary

This document details the step-by-step implementation, configuration, security permit configuration, and architectural integration of **SpringDoc OpenAPI 3.0 and Swagger UI** in the reForm backend monolith (`backend/`).

### 🎯 Key Capabilities Introduced
1. **Automated Interactive API Dashboard**: Live visual API browser hosted at `http://localhost:8080/swagger-ui.html`.
2. **JWT Bearer Security Scheme**: Built-in "Authorize" modal in Swagger UI allowing developers to authenticate (`Bearer <JWT>`) and test protected endpoints directly.
3. **OpenAPI 3.0 Spec Auto-Generation**: Auto-generated machine-readable spec at `/v3/api-docs` (JSON) and `/v3/api-docs.yaml` (YAML) for Next.js frontend SDK generation.

---

## 1. Architectural Choice: Code-First vs. Spec-First

We evaluated two architectural approaches for OpenAPI:

| Architectural Approach | How It Works | Selected Choice & Rationale |
| :--- | :--- | :--- |
| **Code-First (Selected)** | SpringDoc automatically scans `@RestController`, `@Operation`, and DTO annotations at runtime to build the spec dynamically in memory. | ✅ **SELECTED**: Zero maintenance overhead. When Java controllers or DTOs change, documentation updates automatically with zero broken contracts. |
| **Spec-First / Static File** | Developers manually edit `openapi.yml` by hand before coding. | ❌ Rejected for primary workflow to prevent spec drift. (Can be exported statically anytime via `/v3/api-docs.yaml`). |

---

## 2. Step-by-Step Implementation Walkthrough

### Step 1: Add Maven Dependency (`pom.xml`)
- **File**: [`pom.xml`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/pom.xml)
- **WHY**: To pull in SpringDoc OpenAPI WebMVC starter supporting Spring Boot 3+ / 4+.
- **HOW**: Added property `<springdoc.version>2.8.5</springdoc.version>` and dependency:
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>${springdoc.version}</version>
</dependency>
```

---

### Step 2: Configure SpringDoc Settings (`application.yml`)
- **File**: [`application.yml`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/src/main/resources/application.yml)
- **WHY**: To customize Swagger UI paths and ordering behavior.
- **HOW**: Added `springdoc` configuration block:
```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: alpha
    doc-expansion: none
  api-docs:
    path: /v3/api-docs
```

---

### Step 3: Create OpenAPI & Security Configuration (`OpenApiConfig.java`)
- **File**: [`OpenApiConfig.java`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java/com/reForm/backend/config/OpenApiConfig.java) `[NEW]`
- **WHY**: To configure global API metadata, title, version, and the JWT Bearer authentication scheme.
- **HOW**: Implemented Spring `@Configuration` bean annotated with `@OpenAPIDefinition` and `@SecurityScheme`:

```java
package com.reForm.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "reForm Enterprise Platform API",
                version = "1.0.0",
                description = "REST API & WebSocket infrastructure for reForm Omni-modal Voice/Visual Form Builder & Conversational AI Engine."
        ),
        security = {
                @SecurityRequirement(name = "bearerAuth")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Enter JWT token obtained from POST /api/v1/auth/login or POST /api/v1/auth/register."
)
public class OpenApiConfig {
}
```

---

### Step 4: Permit Public Access in Spring Security (`SecurityConfig.java`)
- **File**: [`SecurityConfig.java`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java/com/reForm/backend/auth/config/SecurityConfig.java) `[MODIFY]`
- **WHY**: Spring Security blocks unauthenticated access to all routes by default, returning `401 Unauthorized` when trying to open `/swagger-ui.html`.
- **HOW**: Added `/swagger-ui/**`, `/v3/api-docs/**`, and `/swagger-ui.html` to `requestMatchers(...).permitAll()`:

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/v1/auth/**").permitAll()
        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll() // Added
        .anyRequest().authenticated()
)
```

---

## 3. How to Use Swagger UI for API Testing

1. **Boot Backend**: Run `./mvnw spring-boot:run` in `backend/`.
2. **Access Swagger UI**: Open `http://localhost:8080/swagger-ui.html` in your web browser.
3. **Authenticate**:
   - Click the green **"Authorize"** button at the top right.
   - Enter your JWT token (`eyJhbGci...`) into the `Value` field.
   - Click **Authorize** $\rightarrow$ **Close**.
4. **Test Endpoints**: Expand any endpoint (e.g. `POST /api/v1/forms`), click **"Try it out"**, fill in parameters, and click **Execute**!

---

## 4. Exporting `openapi.yml` for Frontend Code Generation

If the Next.js frontend team needs a static `openapi.yml` file to generate TypeScript API SDKs:

1. Start backend server.
2. Open terminal in workspace root and run:
   ```bash
   curl http://localhost:8080/v3/api-docs.yaml > backend/src/main/resources/openapi.yml
   ```
3. Use `@openapitools/openapi-generator-cli` in `frontend/` to auto-generate TypeScript client hooks!
