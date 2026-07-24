# WebSocket & Real-Time Communication: Backtrack Mental Model
**Document Version:** 5.0  
**Target Platform:** reForm (Modular Monolith)  
**Author:** Senior Technical Lead  

---

## 1. The Root Problem: I Want Users to Talk to an AI in Real-Time
### ❓ Question:
*"I want a candidate to be able to speak into their microphone. The AI should hear them, respond, and speak back — all in real-time. How do I do this?"*

### 💡 The Solution Direction:
We need a **persistent, bidirectional communication channel** between the browser and the server. The candidate's voice must stream continuously in one direction, and the AI's audio response must stream back simultaneously.

---

## 2. Sub-Problem: Can I Use Regular HTTP for This?
### ❓ Question:
*"I already have REST controllers. Can I just have the browser POST audio bytes every second to an endpoint?"*

### ⚠️ The Conflict:
HTTP is a **request-response** protocol. For every request, there must be a full network handshake, headers are sent, the server processes, and a response is returned. This cycle is designed for discrete, independent operations — not for streaming 50 audio frames per second.

If you tried:
* **Polling** (browser asks "any new audio?" every 100ms): Creates 10 requests/second per user. Under load, catastrophic overhead.
* **Long Polling** (browser holds the connection open until the server responds): Hacky, one-directional, high latency.
* **HTTP Chunked Streaming**: One direction only. The server can stream to the client, but the client cannot simultaneously stream to the server on the same connection.

### 💡 The Solution:
We need **WebSockets** (RFC 6455): a protocol that upgrades an HTTP connection into a **persistent, full-duplex TCP socket**. Both sides can send data at any time, simultaneously, with no repeated handshakes.

```text
  HTTP (Half-Duplex, Stateless)         WebSocket (Full-Duplex, Persistent)
  
  Client ──── Request ────► Server      Client ◄══════════════════► Server
  Client ◄─── Response ─── Server      (TCP socket stays open forever)
  Client ──── Request ────► Server      (both sides stream simultaneously)
  ...
```

---

## 3. Sub-Problem: What Kind of WebSocket Do I Use?
### ❓ Question:
*"I read that Spring supports STOMP and also raw WebSockets. Which one do I use for voice?"*

### ⚠️ The Conflict:
STOMP (Simple Text Oriented Messaging Protocol) is a sub-protocol on top of WebSockets. It adds a structured text envelope around every message with command names, headers, and destinations — designed for Pub/Sub messaging patterns like chat rooms.

If you wrap raw audio bytes inside STOMP frames:
* Every binary audio chunk gains kilobytes of text header overhead.
* At 50 frames/second, this is 50 × wasted text parsing per user per second.
* STOMP is not designed for binary streaming — it is designed for discrete text messages.

### 💡 The Solution:
We use **Raw Binary WebSockets**. This sends pure, unadorned binary frames with zero overhead. Gemini Live API, Twilio Media Streams, and all professional voice streaming platforms are built on raw binary WebSocket transport.

---

## 4. Sub-Problem: What Class Do I Write to Handle the Connection?
### ❓ Question:
*"Okay, I chose raw binary WebSockets. Now what class do I write in Java? Do I extend something? What methods do I override?"*

### 💡 The Solution:
Spring provides `BinaryWebSocketHandler` — an abstract class designed specifically for binary frame WebSocket connections. You extend it and override 5 lifecycle methods:

```text
  BinaryWebSocketHandler (Spring abstract class)
        │
        └── afterConnectionEstablished(session)   ← fires when socket opens
        └── handleBinaryMessage(session, message) ← fires when PCM audio bytes arrive (~50fps)
        └── handleTextMessage(session, message)   ← fires when heartbeat PING frames arrive
        └── handleTransportError(session, error)  ← fires on network failure
        └── afterConnectionClosed(session, status)← fires when socket closes
```

Your class (`VoiceSyncWSHandler`) extends `BinaryWebSocketHandler` and fills in these methods. Spring calls them automatically as events happen on the TCP socket.

---

## 5. Sub-Problem: How Does HTTP Become a WebSocket?
### ❓ Question:
*"The browser connects with HTTP first. How does it turn into a WebSocket connection? Is it a new connection?"*

### 💡 The Solution:
It is NOT a new connection. The process is called the **Protocol Upgrade Handshake**:

```text
  STEP 1: Browser sends an HTTP request with a special header:
  GET /ws/v1/voice?token=JWT_TOKEN HTTP/1.1
  Upgrade: websocket
  Connection: Upgrade
  
  STEP 2: Server accepts and responds:
  HTTP/1.1 101 Switching Protocols
  Upgrade: websocket

  STEP 3: The SAME underlying TCP socket is now a WebSocket.
  No new connection. No new port. The HTTP socket is repurposed.
```

The `101 Switching Protocols` status code is unique — it is the only HTTP response that changes the protocol of the connection rather than terminating it.

---

## 6. Sub-Problem: How Do I Tell Spring to Accept WebSocket Connections?
### ❓ Question:
*"I have my handler class (`VoiceSyncWSHandler`). How does Spring know to route incoming connections to it? I have no `@RequestMapping` here — how does URL routing work for WebSockets?"*

### 💡 The Solution:
Spring provides a `WebSocketConfigurer` interface. You create `WebSocketConfig.java` implementing it and override `registerWebSocketHandlers()`. This maps a URL path to your handler class.

```text
  WebSocketConfig.registerWebSocketHandlers()
        │
        └──► registry.addHandler(voiceSyncWSHandler, "/ws/v1/voice")
                    │
                    └──► Spring now routes HTTP GET /ws/v1/voice
                         → upgrade handshake → VoiceSyncWSHandler
```

---

## 7. Sub-Problem: How Do I Validate the User During a WebSocket Connection?
### ❓ Question:
*"My REST endpoints are protected by JWT filters. But once the WebSocket upgrades, no more HTTP requests are made — so when does JWT validation happen? I cannot validate the token on every audio frame, can I?"*

### ⚠️ The Conflict:
Spring Security's JWT filter runs on HTTP requests. After the protocol upgrades, subsequent WebSocket frames are binary frames — they never hit the HTTP filter chain. Also, browsers cannot set custom HTTP headers on WebSocket connections.

### 💡 The Solution:
**The Handshake Interceptor** — a one-time security gate that runs during the HTTP upgrade.

When `WebSocketConfig` registers the handler, you chain `JwtHandshakeInterceptor` onto it. This interceptor runs its `beforeHandshake()` method **during the upgrade HTTP request** — before the protocol switches. It reads `?token=JWT_TOKEN` from the URL query parameter, validates it via `ITokenProvider`, and copies `userId` and `role` into `attributes`.

```text
  Browser ──( GET /ws/v1/voice?token=abc )──► HandshakeInterceptor.beforeHandshake()
                                                      │
                                             validate JWT, extract userId & Role
                                             copy into attributes{} map
                                                      │
                                             (if invalid: return HTTP 401, abort upgrade)
                                                      │
  Browser ◄─── 101 Switching Protocols ──────────────┘
  
  (All subsequent binary frames read attributes{} — no re-validation needed)
```

---

## 8. Sub-Problem: How Do I Know Who Each Active Socket Belongs To?
### ❓ Question:
*"Once a connection is open, the WebSocket session exists somewhere in Spring's memory. How do I look up a specific user's session quickly among 500 concurrent users?"*

### 💡 The Solution:
We maintain a **local in-memory map** inside `VoiceSyncWSHandler`:

```text
  activeSessions: Map<userId → WebSocketSession>
  
  userId "usr_123" ──► WebSocketSession (live TCP socket handle to Browser A)
  userId "usr_456" ──► WebSocketSession (live TCP socket handle to Browser B)
```

When `afterConnectionEstablished()` fires, we put the session in the map.  
When `afterConnectionClosed()` fires, we remove it.

---

## 9. Sub-Problem: What Map Type Do I Use?
### ❓ Question:
*"I know `HashMap`. But my handler is a singleton and 500 users can connect at the same time. If 500 threads write to the same `HashMap` simultaneously, is that safe?"*

### ⚠️ The Conflict:
A plain `HashMap` is not thread-safe. Two concurrent `put()` calls can corrupt its internal bucket array structure, causing lost entries or infinite loops.

### 💡 The Solution:
`ConcurrentHashMap` partitions its internal storage into segments. Threads writing to different keys almost never block each other, and reads require no lock at all.

---

## 10. Sub-Problem: Does Using ConcurrentHashMap Make My App Stateful?
### ❓ Question:
*"I keep hearing that stateless is good. But now I have a map of WebSocket sessions in one server's RAM. Does this mean my app is stateful?"*

### 💡 The Solution:
WebSockets are persistent TCP sockets. A socket object physically exists in one server's RAM.
* The **server node** is stateful (owns the physical TCP connection).
* The **system** remains scalable because Redis holds the distributed session registry (`session:{userId}`), and sticky sessions on the load balancer route client traffic to the node holding their socket.

---

## 11. Sub-Problem: What Happens If the Server Crashes Mid-Session?
### ❓ Question:
*"If the server crashes, `afterConnectionClosed()` never runs. The Redis key stays forever. How do I prevent ghost sessions?"*

### 💡 The Solution: Three parts working together.
1. **Explicit TTL (2 Hours):** `SessionTracker.registerSession()` writes the session hash and calls `redisTemplate.expire(sessionKey, SESSION_TTL)` to set a 2-hour lease.
2. **Heartbeat PING/PONG Frames:** Active clients send `PING` text frames every 30 seconds. `VoiceSyncWSHandler.handleTextMessage()` intercepts `PING`, calls `sessionTracker.refreshTTL(userId)`, and replies `PONG`.
3. **Overwrite on Reconnect:** Reconnecting users overwrite any ghost key using `opsForHash().putAll()`.

```text
  Server Crashes ──► afterConnectionClosed() NEVER runs
                 ──► Redis key stays (ghost session)
                 ──► Heartbeats stop → TTL countdown: 2 hours to automatic expiry
```

---

## 12. Sub-Problem: Microphones Stream Continuous Silence — How Do I Stop Billing Spikes?
### ❓ Question:
*"In `handleBinaryMessage`, packets arrive 50 times per second. If the user stops talking but leaves the mic open, continuous background noise sends input audio tokens to Gemini, costing $3.00/1M tokens. How do I fix this?"*

### 💡 The Solution: Frontend VAD (Voice Activity Detection)
We place a WebAssembly VAD module (`@ricky0123/vad-web`) in the candidate's browser (Next.js):
* **User Speaking:** VAD detects voice $\rightarrow$ browser streams binary PCM packets over WSS.
* **User Silent:** VAD detects silence $\rightarrow$ browser **pauses sending binary frames**.

Backend `handleBinaryMessage()` simply stays idle during silence! This cuts input token bills by **40% to 50%** with zero backend code changes.

---

## 13. Sub-Problem: How Do I Support Multiple AI Vendors & Models Without `if/else` Spaghetti?
### ❓ Question:
*"What if I want Mode 4 (Gemini 3.1 Live) for premium voice, Mode 3 (Cascaded Deepgram + Cartesia) for budget forms, or Gemini 3.6 Flash for text? How do I structure this without massive `if/else` checks?"*

### 💡 The Solution: Strategy & Adapter Patterns
1. **`IAiVoiceAdapter` (Bridge/Adapter Pattern):** Decouples `VoiceSyncWSHandler` from provider implementations (`GeminiLiveVoiceAdapter`).
2. **`IAiModelProviderStrategy` (Strategy Pattern):** Defines model strategies (`Gemini31LiveModelStrategy`, `Gemini35FlashModelStrategy`). `SessionContextService` uses Spring's `List<IAiModelProviderStrategy>` to resolve target models dynamically using stream matching.

---

## 14. Sub-Problem: Form Builder & Form Filler Both Need AI — Where Should These Classes Live?
### ❓ Question:
*"Mode 4 is used by Form Builders (John Co-Builder Assistant in `form/`) and Form Fillers (Sarah Interviewer in `submission/`). If these classes live in `submission/`, `form/` has to import from `submission/`, creating circular dependencies. Where should this code live?"*

### 💡 The Solution: Package Refactoring into `com.reForm.backend.ai`
Extract all AI models, strategies, state trackers, services, and WebSocket handlers into a dedicated root-level infrastructure module: **`com.reForm.backend.ai`**.

---

## 15. Sub-Problem: How Does Mode 4 Trigger Real-Time Layout Changes While Talking?
### ❓ Question:
*"When John says 'Add a contact section' in Mode 4, how does the AI keep speaking out loud AND update the form preview canvas on screen at the exact same time?"*

### 💡 The Solution: Function Calling (`toolCall`) + Spring Async Events
1. **JSON Schema Tool Declaration:** During WSS setup, we register the `modifyFormLayout` tool schema.
2. **LLM Emits `toolCall` Frame:** Gemini 3.1 Live stops text output and returns a `toolCall` JSON frame.
3. **Adapter Intercepts & Dispatches Event:** `GeminiLiveVoiceAdapter` catches the `toolCall` and dispatches `FormLayoutModificationEvent` via Spring's `ApplicationEventPublisher`.
4. **Async Layout Agent:** `@Async @EventListener` catches the event, invokes Gemini 3.6 Flash (Mode 2), generates form block DTOs, saves to PostgreSQL, and broadcasts a WebSocket update to John's canvas pane.
5. **Dual Response:** John hears *"Adding contact section now"* out loud while watching the fields appear live on his screen!

---

## 16. Sub-Problem: Does Mode 3 Need 3 Persistent WebSockets Per User?
### ❓ Question:
*"Mode 3 uses Deepgram STT, Gemini 3.6 Flash, and Cartesia TTS. Does every single user need 3 open WebSocket connections to the backend?"*

### 💡 The Solution: 2 WebSockets + 1 Stateless HTTP REST Optimization
* **Audio Requires Sockets:** Raw binary audio streams for STT (Deepgram) and TTS (Cartesia) **must** have 1 dedicated socket per user session to maintain continuous audio context.
* **LLM is Stateless:** Gemini 3.6 Flash processes text and outputs text/tools. It does **NOT** need a persistent socket!
* **The Optimization:** Mode 3 uses **1 Inbound Client WSS + 1 Outbound Deepgram WSS + 1 Outbound Cartesia WSS**, while Gemini 3.6 Flash is executed via **Stateless HTTP REST** (~200ms).
* **Single Node Capacity:** A single 4 vCPU / 8 GB RAM Spring Boot instance handles **2,000 to 3,000 active concurrent voice sessions** (using ~45 MB RAM for 3,000 TCP sockets).

---

## 17. Sub-Problem: What Happens in Mode 3 When the User Interrupts the AI?
### ❓ Question:
*"If the AI is speaking out loud via Cartesia TTS in Mode 3, and the candidate interrupts and starts talking, how do we stop the AI from talking over the candidate?"*

### 💡 The Solution: Utterance Detection & Interruption Flushing
1. **Candidate Speaks:** Candidate speaks into microphone while AI audio is playing.
2. **Deepgram Detects Speech:** Deepgram Nova-3 STT emits a `{ "speech_started": true }` JSON event.
3. **Adapter Intercepts:** `CascadedVoiceAdapter` catches the event.
4. **Flush Signal:** Sends a cancellation frame to Cartesia TTS (`{"cancel": true}`) and a clear buffer signal to the client browser (`FLUSH_AUDIO_BUFFER`).
5. **Client Audio Cleansed:** The browser clears its WebAudio queue immediately, halting AI speech in mid-sentence!

---

## 18. Sub-Problem: How Do Users Bring Their Own API Key (BYOK) Safely?
### ❓ Question:
*"If a user wants to use their own Gemini API key so reForm doesn't bill them for voice minutes, how do we store their API key securely without exposing raw secrets in database tables?"*

### 💡 The Solution: AES-256-GCM Database Encryption + Split-Second RAM Decryption
1. **DB Storage:** API keys are stored in PostgreSQL under `workspaces.byok_api_key_encrypted` encrypted using **AES-256-GCM**.
2. **RAM Lifetime:** `SessionContextService.resolveApiKey(workspaceId)` decrypts the key in server RAM only for the split-second required to establish the outbound WebSocket connection to Google.
3. **Fallback:** If no BYOK key exists, `resolveApiKey()` falls back to `app.ai.google.default-api-key` in `application.yml`.
4. **Billing Waiver:** When BYOK is present, `BillingAgent` sets `chargeCredits = false` for voice minutes.

---

## 19. Sub-Problem: Why Shouldn't System Prompts Be Hardcoded Strings in Java Classes?
### ❓ Question:
*"Why can't I just put the system prompt in a `public static final String SYSTEM_PROMPT` inside my Java class?"*

### ⚠️ The Conflict:
Hardcoding prompts inside Java classes forces full application recompilation and redeployment whenever a recruiter modifies their persona rules or candidate goals.

### 💡 The Solution: `FormAgentConfig` Entity
We create `FormAgentConfig.java` bound 1-to-1 with `Form`:
* Fields: `modelKey`, `systemPrompt`, `voiceName`, `temperature`, `byokApiKeyEncrypted`.
* Runtime Compilation: When a session starts, `SessionContextService` queries `FormAgentConfig`, appends target goals, and compiles the final setup payload dynamically.

---

## 20. Sub-Problem: Why Avoid `@Autowired` Field Injection in Spring Services?
### ❓ Question:
*"I see many tutorials using `@Autowired private MyService myService;`. Why do senior architects say this is bad practice, and what should I use instead?"*

### ⚠️ The Conflict:
Field injection breaks immutability (fields cannot be `final`), hides dependency counts, makes unit testing without Spring contexts impossible, and delays circular dependency errors until runtime.

### 💡 The Solution: Lombok `@RequiredArgsConstructor` + `private final` Fields
Annotate `@Service` classes with Lombok's `@RequiredArgsConstructor` and mark all dependencies as `private final`:
```java
@Service
@RequiredArgsConstructor
public class SessionContextService {
    private final ApplicationEventPublisher eventPublisher;
    private final WorkspaceRepository workspaceRepository;
}
```

For event objects, use Java 21 **`record`** (the ultimate immutable POJO):
```java
public record FormLayoutModificationEvent(String formId, String userIntent) {}
```

---

## 21. Sub-Problem: Why Build a Model Context Protocol (MCP) Server?
### ❓ Question:
*"I already have REST APIs and Function Calling (`toolCall`). Why do I need a Model Context Protocol (MCP) Server?"*

### 💡 The Solution: Enable Bring Your Own AI Client (BYO-AI)
* **REST APIs:** Used by human web browsers.
* **Native Tool Calling:** Used internally by Gemini Live.
* **MCP Server (`https://api.reform.com/mcp`):** Uses Anthropic's open JSON-RPC 2.0 standard over SSE to expose **Resources** (`reform://`), **Tools**, and **Prompts** to external AI clients (Claude Desktop, Cursor IDE, ChatGPT).
* **Commercial Value:** Enables recruiters to manage forms directly from their favorite desktop AI client!

---

## 22. Complete Backtracking Mental Model Tree

```text
  ROOT GOAL: User can speak to AI in real-time
  │
  ├── ❓ Can I use HTTP?
  │         └── HTTP is request-response — cannot stream continuous 50fps audio
  │               └── 💡 WebSockets (persistent full-duplex TCP)
  │
  ├── ❓ STOMP or raw WebSockets?
  │         └── STOMP adds text overhead on binary audio
  │               └── 💡 Raw Binary WebSockets (BinaryWebSocketHandler)
  │
  ├── ❓ How does HTTP become a WebSocket?
  │         └── 💡 101 Switching Protocols upgrade (same TCP socket repurposed)
  │
  ├── ❓ How does Spring route WebSocket connections to my handler?
  │         └── 💡 WebSocketConfig implements WebSocketConfigurer (maps /ws/v1/voice)
  │
  ├── ❓ How do I validate the user? JWT filters don't run on WS frames.
  │         └── Browsers cannot send auth headers on WebSocket
  │               └── 💡 JwtHandshakeInterceptor.beforeHandshake()
  │                     reads ?token= query param, validates JWT, writes userId & role to attributes
  │
  ├── ❓ How do I look up which session belongs to which user?
  │         └── 💡 ConcurrentHashMap<userId, WebSocketSession> inside VoiceSyncWSHandler
  │
  ├── ❓ How do I coordinate active sessions across multiple servers?
  │         └── 💡 SessionTracker service writes session:{userId} to Redis with explicit 2h TTL
  │               Heartbeat PING frames trigger refreshTTL() to prevent ghost sessions
  │
  ├── ❓ How do I prevent background noise from racking up $3.00/1M audio input costs?
  │         └── Continuous mic streaming sends packets during silence
  │               └── 💡 Frontend VAD (Voice Activity Detection)
  │                     Pauses binary frame transmission during silence → 40-50% cost reduction
  │
  ├── ❓ How do I support multiple models/vendors cleanly without if/else?
  │         └── Hardcoding vendor calls couples socket code
  │               └── 💡 Strategy & Adapter Patterns (IAiVoiceAdapter & IAiModelProviderStrategy)
  │                     SessionContextService resolves model strategy dynamically via streams
  │
  ├── ❓ Form Builder & Filler both use AI — where should this code live?
  │         └── Placing in submission/ creates circular dependencies with form/
  │               └── 💡 Package Refactoring to root module com.reForm.backend.ai
  │                     All AI, state, strategies, and WebSocket gateways live in com.reForm.backend.ai
  │
  ├── ❓ How does Mode 4 change form layout while talking?
  │         └── 💡 Function Calling (toolCall) + Spring ApplicationEventPublisher + @Async LayoutAgent
  │
  ├── ❓ Does Mode 3 need 3 persistent WebSockets per user?
  │         └── 💡 2 WebSockets (Deepgram + Cartesia) + 1 Stateless HTTP REST call (Gemini 3.6 Flash)
  │               Handles 2,000 to 3,000 active sessions per server node
  │
  ├── ❓ How to stop AI from talking over user in Mode 3?
  │         └── 💡 Deepgram speech_started event → Cartesia cancel frame → client FLUSH_AUDIO_BUFFER
  │
  ├── ❓ How do users supply BYOK API keys securely?
  │         └── 💡 AES-256-GCM encryption in PostgreSQL → RAM decryption lifetime in SessionContextService
  │
  ├── ❓ Why avoid hardcoded prompts in Java classes?
  │         └── 💡 FormAgentConfig entity bound 1-to-1 with Form for runtime prompt compilation
  │
  ├── ❓ Why avoid @Autowired field injection?
  │         └── 💡 Lombok @RequiredArgsConstructor + private final fields + Java 21 record POJO events
  │
  └── ❓ Why build an MCP server?
            └── 💡 Exposes JSON-RPC 2.0 over SSE (Resources, Tools, Prompts) for BYO-AI client integration
```
