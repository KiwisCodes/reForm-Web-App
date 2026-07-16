package com.reForm.backend.core.config;


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
import tools.jackson.databind.ObjectMapper;

/**
 * REDIS & LETTUCE SYSTEM CONFIGURATION
 * 
 * In this project, we utilize three main layers for rate limiting and state storage:
 * 1. Redis: The database server running in a separate Docker container/host.
 * 2. Lettuce: The Java network driver library (client) that connects our code to the Redis server.
 * 3. Bucket4j: The rate-limiting logic engine, which uses Lettuce to store and fetch counts from Redis.
 * 
 * =========================================================================================
 * COMPONENT RELATIONSHIP FLOW
 * =========================================================================================
 * 
 * [User Request] ──> [Spring Boot Backend]
 *                          │
 *         ┌────────────────┴────────────────┐
 *         ▼                                 ▼
 *  [Pathway A: Rate Limiter]       [Pathway B: Data Cache]
 *   - Safeguards WS / HTTP API      - Manages User Sessions / State
 *   - Uses Stateful Connection      - Uses RedisTemplate
 *   - Fast Binary CAS Actions       - Serializes Java Objects to JSON
 *   - codec: <byte[], byte[]>       - Uses: Jackson ObjectMapper
 *         │                                 │
 *         └────────────────┬────────────────┘
 *                          ▼
 *            [Lettuce Connection Driver]
 *                          │ (RESP TCP Packets over port 6379)
 *                          ▼
 *                 [Redis Docker Server]
 * 
 */
@Slf4j
@Configuration
public class RedisConfig {

    // Host address of the centralized Redis server (read from application.yml)
    @Value("${spring.data.redis.host}")
    private String redisHost;

    // Communication port of the centralized Redis server (read from application.yml)
    @Value("${spring.data.redis.port}")
    private int redisPort;

    /**
     * Lettuce Connection Factory Bean
     * 
     * Parameters: None (Acts as a primary producer of connection configurations).
     * 
     * What is it:
     * This class manages the connection pool configuration, handling client socket lifecycles 
     * and reusing connections to the database to minimize overhead.
     * 
     * Why we need it:
     * Spring Data Redis templates require a connection factory bean to talk to the database.
     * Exposing it with @Bean ensures Spring manages its initialization, pooling, and shutdown.
     */
    @Bean
    public LettuceConnectionFactory lettuceConnectionFactory() {
        // RedisStandaloneConfiguration:
        // A Spring class representing the connection metadata for a single (standalone) Redis server.
        // It holds the host and port mapping and is used to initialize the connection factory.
        RedisStandaloneConfiguration redisStandaloneConfiguration = 
            new RedisStandaloneConfiguration(redisHost, redisPort);
        return new LettuceConnectionFactory(redisStandaloneConfiguration);
    }

    /**
     * Spring RedisTemplate Bean
     * 
     * Parameters:
     * 1. LettuceConnectionFactory connectionFactory: Injecting the Spring-managed factory bean to 
     *    reuse the same connection pool, preventing socket exhaustion.
     * 2. ObjectMapper objectMapper: Injecting Spring's auto-configured JSON mapper. We pass this 
     *    into the serializer to customize how objects (like LocalDateTime) are mapped to JSON strings.
     * 
     * What is it:
     * Provides high-level Java object operations for reading and writing data in Redis.
     * 
     * Why we need it:
     * Used for general key-value storage, session tracking, and user data cache mappings.
     * 
     * Serializers:
     * - KeySerializer: We use StringRedisSerializer to write plain string keys instead of raw binary formats.
     * - ValueSerializer: We use GenericJacksonJsonRedisSerializer (replaced deprecated GenericJackson2JsonRedisSerializer 
     *   for Spring Boot 4.0+) constructed with our custom ObjectMapper to write clean, formatted JSON strings for complex Java objects.
     * 
     * What ObjectMapper does here:
     * ObjectMapper is the core engine of the Jackson library. It translates Java Objects (e.g. UserSession) 
     * into readable JSON strings when saving, and parses JSON strings back into Java Objects when reading. 
     * By injecting Spring's configured ObjectMapper, we guarantee that dates, times, and field naming 
     * conventions in Redis are serialized identically to how they are processed in our web API JSON controllers.
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
     * 
     * Parameters: None (Prepares client network configuration).
     * 
     * What is it:
     * The direct database client driver from the Lettuce library. (Note: Redis is the database *server*, 
     * while RedisClient is the *Java code driver instance* running in JVM memory connecting to it).
     * 
     * Why we need it:
     * Bucket4j does not use Spring's high-level templates. It requires a direct Lettuce driver connection 
     * to perform raw byte Compare-And-Swap (CAS) commands on Redis keys with minimal network latency.
     * 
     * Why 'destroyMethod = "shutdown"':
     * RedisClient spins up underlying Netty network threads to execute asynchronous non-blocking I/O.
     * When the Spring container stops, we must clean up these thread pools. Specifying destroyMethod 
     * instructs Spring to automatically run RedisClient.shutdown() when the app stops, preventing thread memory leaks.
     */
    @Bean(destroyMethod = "shutdown")
    public RedisClient lettuceClient() {
        // RedisURI:
        // A Lettuce client class defining the location of the target Redis server as a URI string.
        RedisURI redisURI = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .build();
        log.info("Configuring native Lettuce client for Bucket4j targeting: {}:{}", redisHost, redisPort);
        return RedisClient.create(redisURI);
    }

    /**
     * Stateful Redis Connection Bean
     * 
     * Parameters:
     * - RedisClient redisClient: Injecting the configured client bean that runs the Netty I/O threads.
     * 
     * What is it:
     * Represents a single, thread-safe, duplex TCP connection to the Redis server.
     * 
     * Why we use <byte[], byte[]>:
     * The rate-limiting library (Bucket4j) serializes the state of token buckets into raw binary data 
     * (byte arrays) rather than text strings. Storing keys and values as raw byte[]:
     * 1. Eliminates serialization overhead (no conversion to JSON or UTF-8 strings).
     * 2. Maximizes network performance by keeping message payloads as small as possible.
     * 3. Utilizes Redis's native binary-safe storage capability.
     * 
     * Why 'destroyMethod = "close"':
     * Keeps an open socket connection to Redis. If not closed when the application shuts down, 
     * the connection remains active, leaking file descriptors and server sockets. Declaring destroyMethod 
     * tells Spring to execute connection.close() during shutdown, closing the connection cleanly.
     */
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<byte[], byte[]> lettuceStatefulConnection(RedisClient redisClient) {
        return redisClient.connect(ByteArrayCodec.INSTANCE);
    }
}
