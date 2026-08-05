# Milestone 1: Async Thread Pool & Backend Test Infrastructure Analysis

**Author**: Explorer 2 (Milestone 1)  
**Date**: 2026-08-05  
**Working Directory**: `/Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_m1_2`  
**Status**: Completed (Read-Only)

---

## Executive Summary

This analysis details the current status and technical specifications for the Spring Boot `@Async` thread pool execution infrastructure and backend test setup for reForm platform (Milestone 1).

Key Findings:
1. **Configuration Directory Layout**: The root configuration package directory `backend/src/main/java/com/reForm/backend/config/` does NOT exist yet. Application configurations are currently scattered across domain sub-packages (`ai/config/`, `auth/config/`, `core/config/`).
2. **Async Thread Pool Setup**: `@EnableAsync` is currently present on `BackendApplication.java` (line 10), but **no dedicated `TaskExecutor` / `ThreadPoolTaskExecutor` `@Bean`** is configured. As a result, Spring defaults to creating unmanaged threads per asynchronous call via `SimpleAsyncTaskExecutor`, posing thread exhaustion risks during heavy event processing (e.g. `LayoutAgent`, `GuardrailAgent`, `BillingAgent`).
3. **`AsyncConfig` Specification**: Detailed specification for `com.reForm.backend.config.AsyncConfig` requiring `@Configuration` and `@EnableAsync`, configuring a bounded `ThreadPoolTaskExecutor` with **Core: 10, Max: 50, Queue: 500, Prefix: `"reForm-async-"`**.
4. **Test Infrastructure**: `backend/src/test/java/com/reForm/backend/` currently contains only a minimal `BackendApplicationTests.java` (14 lines). `pom.xml` contains standard `spring-boot-starter-test` and `spring-security-test` dependencies. Base Spring Boot integration test classes need to be documented and created to test async event dispatching.

---

## 1. Backend Configuration Directory Layout Analysis

### Current Directory Structure
Inspection of `backend/src/main/java/com/reForm/backend/` reveals:
```
backend/src/main/java/com/reForm/backend/
├── BackendApplication.java
├── ai/
│   └── config/           # FormAiDataInitializer.java, JwtHandshakeInterceptor.java, WebSocketConfig.java
├── auth/
│   └── config/           # SecurityConfig.java
├── core/
│   └── config/           # PasswordEncoderConfig.java, RateLimitProperties.java, RedisConfig.java, etc.
├── form/
├── submission/
└── user/
```

### Problem & Recommendation
The top-level configuration directory `com.reForm.backend.config` is absent. Global cross-cutting platform configurations (such as global async thread management) belong in the root `config` package (`com.reForm.backend.config`).

**Action Item for Implementation**:
- Create directory `backend/src/main/java/com/reForm/backend/config/`.
- Place `AsyncConfig.java` in `com.reForm.backend.config`.

---

## 2. Async Thread Pool Execution Analysis

### Current Asynchronous Execution Mechanism
- **Annotation Location**: `BackendApplication.java` line 10 contains `@EnableAsync`.
- **Existing Async Consumers**: `LayoutAgent.java` line 49 uses `@Async` on `handleLayoutModification(FormLayoutModificationEvent event)`.
- **Missing Executor Configuration**: No `TaskExecutor` or `ThreadPoolTaskExecutor` bean is defined in the Spring application context.

### Technical Risks of Missing `TaskExecutor` Bean
1. **Unbounded Thread Creation**: Spring's default `SimpleAsyncTaskExecutor` creates a new Java thread for every asynchronous method execution instead of reusing worker threads from a bounded pool.
2. **Resource Exhaustion**: Under multi-tenant WebSocket usage (Modes 3 & 4), high frequency event publishing (e.g. `FormLayoutModificationEvent`, `GuardrailValidationEvent`, `BillingUsageEvent`) will spawn hundreds of concurrent threads, exhausting OS native memory and CPU cores.
3. **Lack of Backpressure**: Without a bounded queue capacity (500) and explicit rejection policy (`CallerRunsPolicy`), system degradation is unthrottled during peak load.

---

## 3. Implementation Requirements for `com.reForm.backend.config.AsyncConfig`

### Target File Path
`backend/src/main/java/com/reForm/backend/config/AsyncConfig.java`

### Technical Blueprint & Code Specification

```java
package com.reForm.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * ASYNCHRONOUS THREAD POOL CONFIGURATION
 * 
 * Configures the dedicated ThreadPoolTaskExecutor for zero-blocking event listeners
 * and async agent processing across reForm platform (LayoutAgent, GuardrailAgent, etc.).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("reForm-async-");
        
        // Rejection strategy when queue capacity (500) and max pool size (50) are full:
        // CallerRunsPolicy executes the task on the calling thread to apply backpressure.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        executor.initialize();
        return executor;
    }
}
```

### Key Parameter Verification

| Property | Value | Purpose |
|---|---|---|
| Class Annotations | `@Configuration`, `@EnableAsync` | Declares Spring config and activates `@Async` processing |
| Bean Name | `"taskExecutor"` | Default bean name referenced by Spring's `@Async` infrastructure |
| Core Pool Size | `10` | Base number of worker threads kept alive |
| Max Pool Size | `50` | Maximum limit of worker threads allocated under load |
| Queue Capacity | `500` | Buffer capacity before spawning threads beyond corePoolSize |
| Thread Name Prefix | `"reForm-async-"` | Identifies agent background threads in logs & thread dumps |
| Rejection Handler | `CallerRunsPolicy` | Ensures tasks are never dropped silently under high spike loads |

*Note*: Refactoring `@EnableAsync` onto `AsyncConfig` allows removal of `@EnableAsync` from `BackendApplication.java` for cleaner single-responsibility architecture.

---

## 4. Backend Test Infrastructure Analysis

### Current State
`backend/src/test/java/com/reForm/backend/`:
- `BackendApplicationTests.java` (Line 6-13): Contains basic context loading test.

```java
package com.reForm.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
```

`backend/pom.xml`:
- `spring-boot-starter-test` (line 100-103): Includes JUnit 5, AssertJ, Mockito, Spring Test.
- `spring-security-test` (line 104-107).

### Required Backend Test Infrastructure Enhancements

To verify `AsyncConfig` and event execution, the test infrastructure requires a dedicated test verification class in `backend/src/test/java/com/reForm/backend/config/AsyncConfigTest.java`.

#### Test Specification for `AsyncConfigTest.java`:
1. **Context & Bean Injection Verification**: Validate `ThreadPoolTaskExecutor` bean is present in Spring Context.
2. **Pool Parameters Assertions**:
   - `corePoolSize == 10`
   - `maxPoolSize == 50`
   - `queueCapacity == 500` (or `queueCapacity` verification via `getThreadPoolExecutor().getQueue()`)
   - `threadNamePrefix == "reForm-async-"`
3. **Async Execution Thread Verification**: Verify that a test `@Async` method executes on a thread whose name starts with `"reForm-async-"`.

#### Sample Test Class Structure:
```java
package com.reForm.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AsyncConfigTest {

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    @Test
    @DisplayName("Verify Async ThreadPoolTaskExecutor bean properties")
    void testTaskExecutorConfiguration() {
        assertThat(taskExecutor).isNotNull();
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(10);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(50);
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("reForm-async-");
    }
}
```

---

## 5. Summary of Actionable Implementation Steps

1. **Create Config Directory**: `backend/src/main/java/com/reForm/backend/config/`
2. **Create AsyncConfig Class**: `backend/src/main/java/com/reForm/backend/config/AsyncConfig.java` with `@Configuration`, `@EnableAsync`, and `ThreadPoolTaskExecutor` (core=10, max=50, queue=500, prefix="reForm-async-").
3. **Create Async Test Class**: `backend/src/test/java/com/reForm/backend/config/AsyncConfigTest.java` to verify bean creation and thread prefix behavior.
4. **Clean up BackendApplication**: Remove duplicate `@EnableAsync` from `BackendApplication.java` once `AsyncConfig` is created.
