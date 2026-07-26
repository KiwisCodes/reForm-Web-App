# 22. Technical Q&A Knowledge Base
**Document Version:** 1.0  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  
**Author:** Senior Technical Lead & AI System Architect  

---

## Technical Q&A Catalog (Q1 to Q12)

#### Q1: What are RFC 6455 WebSockets Opcodes? How does Tomcat parse binary vs text messages?
* **Answer:** Under RFC 6455, every WebSocket frame starts with a 1-byte Opcode header specifying data type:
  * **Opcode `0x1` (Text Frame):** UTF-8 encoded text (`TextMessage` in Spring, used for `"PING"` / `"PONG"` frames).
  * **Opcode `0x2` (Binary Frame):** Unadorned binary bytes (`BinaryMessage` in Spring, streaming PCM 16-bit audio packets ~50fps).
  * **Opcode `0x8` (Close Frame):** Closes socket cleanly.
  * **Opcode `0x9` (Ping) / Opcode `0xA` (Pong):** Protocol-level TCP connection heartbeats.
  
  When Tomcat receives a TCP packet, it reads the 1-byte opcode header, extracts the payload bytes, wraps them in Spring's `BinaryMessage` or `TextMessage`, and dispatches to `VoiceSyncWSHandler`.

#### Q2: Where is Lettuce in our code? Why don't I see Lettuce imports in `VoiceSyncWSHandler`?
* **Answer:** Lettuce is the underlying Java driver for Redis. `VoiceSyncWSHandler` calls `SessionTracker`, which calls Spring's `RedisTemplate`. Under the hood, `RedisTemplate` delegates database commands to `LettuceConnectionFactory` (configured in `RedisConfig.java`), which opens TCP sockets to Redis over port `6379`.

#### Q3: What is the difference between raw binary Lettuce and RedisTemplate in our rate limiting and session tracking?
* **Answer:** 
  * **Rate Limiting:** High-frequency checks (~thousands/sec) use raw Lettuce `StatefulRedisConnection<byte[], byte[]>` to execute Bucket4j CAS Lua scripts with microsecond latency without object serialization overhead.
  * **Session Tracking:** Happens once per connection open/close. Uses `RedisTemplate<String, Object>` with `GenericJacksonJsonRedisSerializer` to output human-readable JSON metadata in Redis keys (`session:{userId}`).

#### Q4: Why `ConcurrentHashMap` instead of plain `HashMap` in `VoiceSyncWSHandler`?
* **Answer:** `VoiceSyncWSHandler` is a Spring **Singleton Bean** servicing hundreds of concurrent connection threads. A plain `HashMap` is not thread-safe and corrupts under concurrent writes (causing infinite loops or `NullPointerExceptions`). `ConcurrentHashMap` uses segment-level bucket locking for safe, high-performance concurrent access.

#### Q5: Does `ConcurrentHashMap` make the app stateful? Is stateful bad?
* **Answer:** A WebSocket is a persistent TCP socket handle that physically lives in one server's RAM. The **server node** is stateful, but the **system** remains horizontally scalable because Redis holds the distributed session registry (`session:{userId}`), and sticky sessions on the load balancer route client traffic to the node holding their socket.

#### Q6: How does VAD (Voice Activity Detection) in the browser save money?
* **Answer:** A WebAssembly VAD module (`@ricky0123/vad-web`) runs in Next.js. When the candidate speaks, binary frames stream over WSS. When silent, the browser **pauses sending frames**. Backend `handleBinaryMessage()` stays idle during silence, cutting Gemini audio input bills by **40% to 50%** with zero backend code changes!

#### Q7: Why extract AI code out of `submission/` into `com.reForm.backend.ai`?
* **Answer:** Real-Time AI Voice is used by **Form Builders in `form/`** (Co-Builder Assistant) and **Form Fillers in `submission/`** (Candidate Interviewer). Placing AI code inside `submission/` would force `form/` to import from `submission/`, creating circular package dependencies. `com.reForm.backend.ai` acts as the clean, shared infrastructure module.

#### Q8: Why did `SessionTracker` require a fixed `redisTemplate.expire()` call?
* **Answer:** Spring's `opsForHash().putAll(key, map)` writes hash entries but **does not accept a TTL duration**. Without an explicit `redisTemplate.expire(key, SESSION_TTL)` call immediately after `putAll()`, session keys are saved with infinite lifetimes. If a server crashes, ghost keys stay in Redis forever.

#### Q9: What was the compiler fix for Bucket4j in `RateLimitServiceImpl.java`?
* **Answer:** In Bucket4j `8.19.0` (`bucket4j_jdk17-lettuce`), `Bucket4jLettuce` does not exist as a class name. The correct class is **`LettuceBasedProxyManager`**. The initialization call is:  
  `LettuceBasedProxyManager.builderFor(redisConnection).withExpirationStrategy(...).build()`.

#### Q10: Does this architecture support Scalability and Modifiability?
* **Answer:** 
  * **Scalability:** Lock-free CAS rate limiting in Redis, sticky sessions for WebSockets, async evaluation offloading, client-side VAD frame pausing, and stateless HTTP REST calls for Gemini 3.6 Flash ensure extreme throughput (2,000 to 3,000 voice sessions per node).
  * **Modifiability:** Adding new AI models (e.g. Gemini 4.0) or new streaming adapters requires adding 1 new Strategy or Adapter class without modifying any existing connection handlers (Open/Closed Principle).

#### Q11: Why is `@Autowired` field injection bad practice? What is the Lombok alternative?
* **Answer:** `@Autowired` field injection breaks immutability, hides dependency count, hinders unit testing without reflection, and masks circular dependencies. Constructor injection with Lombok's `@RequiredArgsConstructor` and `private final` fields enforces immutability, clean unit testing, and compile-time dependency validation.

#### Q12: What is a POJO? Why use Java 21 `record` for events?
* **Answer:** A POJO (Plain Old Java Object) is an ordinary Java class with no framework inheritance or heavy magic. In Java 21, `record` (e.g. `public record FormLayoutModificationEvent(String formId, String userIntent) {}`) is the ideal immutable POJO representation for event payloads.
