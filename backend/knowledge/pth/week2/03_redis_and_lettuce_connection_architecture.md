# Redis & Lettuce: Connection & Serialization Architecture
**Document Version:** 1.0  
**Target Platform:** reForm (Modular Monolith)  
**Author:** Senior Technical Lead

---

## 1. The Redis Infrastructure Stack

To build a distributed rate limiter, we must connect our Spring Boot Java application to our centralized **Redis** server. This connection relies on three layers:

```text
  [ Java Code (Spring Boot) ]
              │ (Calls high-level APIs)
              ▼
  [ Lettuce Client Library ]   <── (The Java Driver: manages sockets & Netty threads)
              │ (RESP Protocol TCP packets over port 6379)
              ▼
    [ Redis Docker Server ]    <── (The Centralized Database Server)
```

1.  **Redis Server:** The actual database server running in a container. It holds data in-memory for microsecond-speed reads and writes.
2.  **Lettuce Driver:** The Java client library. It acts like a JDBC driver, translating Java commands into RESP (REdis Serialization Protocol) TCP packets and sending them to port `6379`.
3.  **RedisClient vs. Redis Server:** **Redis** is the database engine. **`RedisClient`** is a Lettuce Java class running in the JVM that maintains connection configurations, Netty network event loops, and socket factories.

---

## 2. Serialization: Preventing Key Pollution

By default, Spring's `RedisTemplate` uses Java's native JDK binary serialization (`JdkSerializationRedisSerializer`). 

*   **The Problem:** If you write the key `"rate-limit:alex"` to Redis, it gets written as unreadable binary byte sequences:
    `\xac\xed\x00\x05t\x00\x0erate-limit:alex`
    This makes it impossible to inspect keys using the Redis CLI, debug state issues, or set manual TTLs.
*   **The Solution:** We explicitly configure custom serializers in Spring:
    *   **Keys:** We use `StringRedisSerializer` to write plain, human-readable UTF-8 strings.
    *   **Values:** We use JSON serializers to store structured configurations as readable JSON text.

---

## 3. ObjectMapper Integration & Serialization Deprecation

In Spring Boot `4.1.0` (using Spring Data Redis 4.x), **`GenericJackson2JsonRedisSerializer` is terminally deprecated** and slated for removal.

*   **The Replacement:** We use **`GenericJacksonJsonRedisSerializer`** (without the `2`).
*   **Why we inject `ObjectMapper`:** Spring Boot auto-configures a global `ObjectMapper` bean which handles date/time formatting and naming strategies. By passing this bean into `GenericJacksonJsonRedisSerializer(objectMapper)`, we guarantee that serialized objects in Redis share the exact same format rules as our REST JSON controllers, preventing deserialization conflicts.

---

## 4. The Production-Ready Code: `RedisConfig.java`

Here is our verified configuration class:

```java
package com.reForm.backend.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Slf4j
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    /**
     * Lettuce Connection Factory Bean
     * Manages connection pools and client sockets. Annotated with @Bean so 
     * Spring handles its initialization and cleanup.
     */
    @Bean
    public LettuceConnectionFactory lettuceConnectionFactory() {
        // RedisStandaloneConfiguration: Holds host & port metadata for a single instance
        RedisStandaloneConfiguration redisStandaloneConfiguration = 
            new RedisStandaloneConfiguration(redisHost, redisPort);
        return new LettuceConnectionFactory(redisStandaloneConfiguration);
    }

    /**
     * RedisTemplate Bean for General Caching & Session Tracking
     * Overrides default JdkSerialization to output readable Strings (Keys) and JSON (Values).
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        GenericJacksonJsonRedisSerializer jsonSerializer = new GenericJacksonJsonRedisSerializer(objectMapper);
        
        redisTemplate.setKeySerializer(stringRedisSerializer);
        redisTemplate.setHashKeySerializer(stringRedisSerializer);
        redisTemplate.setValueSerializer(jsonSerializer);
        redisTemplate.setHashValueSerializer(jsonSerializer);
        
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    /**
     * Native Lettuce RedisClient Bean
     * Required by Bucket4j to execute raw Compare-And-Swap (CAS) commands on Redis.
     * 
     * Why destroyMethod = "shutdown":
     * Spawns background Netty I/O threads. Specifying destroyMethod tells Spring to 
     * automatically run client.shutdown() on application exit to prevent thread leaks.
     */
    @Bean(destroyMethod = "shutdown")
    public RedisClient lettuceClient() {
        RedisURI redisURI = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .build();
        log.info("Configuring native Lettuce client for Bucket4j targeting: {}:{}", redisHost, redisPort);
        return RedisClient.create(redisURI);
    }

    /**
     * Stateful Redis Connection Bean
     * Uses ByteArrayCodec.INSTANCE because Bucket4j writes raw binary states.
     * 
     * Why destroyMethod = "close":
     * Closes the active TCP socket connection cleanly during application context shutdown.
     */
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<byte[], byte[]> lettuceStatefulConnection(RedisClient redisClient) {
        return redisClient.connect(ByteArrayCodec.INSTANCE);
    }
}
```

---

## 5. Thread Pools & Resource Management (The Destroy Methods)

In enterprise Java, leaving database connections or Netty threads unmanaged leads to resource exhaustion (out-of-memory or file descriptor leaks). We protect our system using Spring's **Bean Lifecycle Management**:

*   **`RedisClient` (`destroyMethod = "shutdown"`)**: Spawns multiple non-blocking Netty threads. Without `shutdown()`, these threads continue running in the background after the application stops, preventing the JVM from shutting down cleanly.
*   **`StatefulRedisConnection` (`destroyMethod = "close"`)**: Occupies an active TCP socket channel. Declaring `close()` guarantees the socket closes and releases the file descriptor back to the operating system when the container exits.
