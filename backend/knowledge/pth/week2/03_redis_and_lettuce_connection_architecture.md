# Redis & Lettuce: Connection & Serialization Architecture
**Document Version:** 1.1  
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
     * Spring RedisTemplate Bean for General Caching & Session Tracking
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
     * Native Lettuce Redis Client required by Bucket4j to execute atomic transaction scripts.
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
     * Stateful binary connection required by Bucket4j's Lettuce-based state manager.
     */
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<byte[], byte[]> lettuceStatefulConnection(RedisClient redisClient) {
        log.info("Opening raw stateful byte-connection for Bucket4j rate limiting");
        return redisClient.connect(ByteArrayCodec.INSTANCE);
    }
}
```

---

## 5. Thread Pools & Resource Management (The Destroy Methods)

In enterprise Java applications, leaving active database connections or Netty threads unmanaged leads to severe **resource leaks** (OOM errors, socket exhaustion, or file descriptor leaks). We protect our system using Spring's **Bean Lifecycle Management** using `destroyMethod`.

```text
       HIERARCHY OF CLEANUP: CLIENT vs. CONNECTION
       
  ┌────────────────────────────────────────────────────────┐
  │ RedisClient (Client Level)                             │
  │  Exposes: shutdown()                                   │
  │  Terminates: Netty EventLoop Thread Pools (CPU cores)  │
  │  ┌──────────────────────────────────────────────────┐  │
  │  │ StatefulRedisConnection (Socket Level)           │  │
  │  │  Exposes: close()                                │  │
  │  │  Terminates: Active TCP socket ports & channels  │  │
  │  └──────────────────────────────────────────────────┘  │
  └────────────────────────────────────────────────────────┘
```

### A. Why does Lettuce spawn multiple threads?
Lettuce is built on top of **Netty**, an asynchronous event-driven network I/O framework. 
*   **The Reactor Pattern:** Instead of blocking a thread waiting for Redis to reply, Netty uses non-blocking I/O. It creates an `EventLoopGroup` containing multiple background threads (usually defaults to `2 * CPU cores`).
*   **Network Polling:** These background threads run continuously in the JVM, polling network socket channels for active read/write operations. When a Redis query returns, Netty worker threads process the TCP packet and pass the data back to your application code.
*   **The Thread Leak Risk:** Because these Netty threads are managed in background executor pools, if you stop your Spring application without explicitly shutting down Netty, these worker threads will continue running in RAM, preventing the JVM from shutting down cleanly and causing memory leaks during server redeployments.

---

### B. The Difference: `close()` vs. `shutdown()`

They target two different levels of resource abstraction:

#### 1. `StatefulRedisConnection.close()` (Socket Level)
*   **Target:** The active TCP socket channel.
*   **What it does:** Closes the physical socket connection between the server and the Redis instance. It releases the allocated local TCP port and frees the operating system **file descriptor** (`FD`).
*   **Why we need it:** An open connection is a network resource. If you stop the app without closing the socket, the connection stays open on the Redis server side in a `CLOSE_WAIT` state. Over time, this leads to **socket exhaustion** (running out of available TCP ports).

#### 2. `RedisClient.shutdown()` (Client / Engine Level)
*   **Target:** The Lettuce client engine and its background thread executors.
*   **What it does:** Terminates the entire client instance, which includes shutting down the Netty `EventLoopGroup` thread pool, cleaning up memory buffers, and releasing internal resources.
*   **Why we need it:** Calling `connection.close()` only terminates that specific connection socket; it does **not** stop the background Netty worker threads. Calling `client.shutdown()` is the final cleanup step that halts all background event loop executors, allowing the JVM to terminate cleanly without leaking threads.
