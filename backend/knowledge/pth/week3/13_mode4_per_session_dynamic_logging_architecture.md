# 13. Mode 4 Per-Session Dynamic Logging Architecture Specification

**Document Version:** 1.0  
**Target System:** reForm Monolith (`com.reForm.backend.ai` & Next.js Frontend)  
**Parent Specification:** [10_mode4_implementation_retrospective_and_js_to_java_mapping.md](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week3/10_mode4_implementation_retrospective_and_js_to_java_mapping.md)  

---

## 1. Overview & Problem Statement

When hundreds of concurrent users stream real-time 16kHz microphone audio frames over WebSockets (~50 frames/sec per user), standard unified console and application log files become severely polluted with interwoven log lines from different user sessions. 

Debugging a single user's voice session (e.g. tracking audio packet drops, transcription latency, or function calling tool responses) becomes extremely difficult when log lines are mixed together across NIO worker threads (`nio-8080-exec-1`, `nio-8080-exec-2`).

To solve this, the reForm backend utilizes **Logback's `SiftingAppender`** combined with **SLF4J's Mapped Diagnostic Context (MDC)** to dynamically stream log statements for each active session into an isolated per-session log file.

---

## 2. SLF4J MDC Context Injection Architecture

### Thread-Local Context Binding
SLF4J `MDC` maintains a thread-local map of contextual key-value pairs. During every WebSocket event callback in `VoiceSyncWSHandler.java` and `GeminiLiveVoiceAdapter.java`, the handler binds the user's ID to `MDC`:

```java
MDC.put("sessionId", userId);
try {
    // Process frame / event
    log.info("Processing frame for user session...");
} finally {
    MDC.remove("sessionId"); // Always cleanup context in finally block
}
```

### Injection Points across Lifecycle Callbacks

| Class & Method | Context Action | Purpose |
| :--- | :--- | :--- |
| `VoiceSyncWSHandler.afterConnectionEstablished` | `MDC.put("sessionId", userId)` | Captures connection init, Redis session registration, and setup JSON transmission. |
| `VoiceSyncWSHandler.handleBinaryMessage` | `MDC.put("sessionId", userId)` | Captures client mic 16kHz PCM audio streaming. |
| `VoiceSyncWSHandler.handleTextMessage` | `MDC.put("sessionId", userId)` | Captures heartbeat PING/PONG and client JSON text frames. |
| `GeminiLiveVoiceAdapter.processGooglePayload` | `MDC.put("sessionId", userId)` | Captures incoming Google Bidi JSON responses, transcriptions, PCM 24kHz audio decoding, and tool calls. |
| `VoiceSyncWSHandler.afterConnectionClosed` | `MDC.put("sessionId", userId)` | Captures socket closure status and Redis presence cleanup. |

---

## 3. Logback `SiftingAppender` Configuration (`logback-spring.xml`)

The `logback-spring.xml` configuration file uses `<discriminator>` to read the `sessionId` MDC key. For every log event, Logback checks if `sessionId` is present:
* If `sessionId` is set to `user_builder_01`, Logback dynamically routes the log line to `logs/sessions/user_builder_01.log`.
* If `sessionId` is missing, Logback falls back to `defaultValue="system"` (`logs/sessions/system.log`).

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <!-- Standard Console Appender -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Sifting Appender for Dynamic Per-Session Log Files -->
    <appender name="SIFT" class="ch.qos.logback.classic.sift.SiftingAppender">
        <discriminator>
            <key>sessionId</key>
            <defaultValue>system</defaultValue>
        </discriminator>
        <sift>
            <appender name="FILE-${sessionId}" class="ch.qos.logback.core.FileAppender">
                <file>logs/sessions/${sessionId}.log</file>
                <append>true</append>
                <encoder>
                    <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level - %msg%n</pattern>
                </encoder>
            </appender>
        </sift>
    </appender>

    <!-- Log Level Configuration -->
    <logger name="com.reForm.backend.ai" level="INFO" />

    <!-- Root Logger -->
    <root level="INFO">
        <appender-ref ref="CONSOLE" />
        <appender-ref ref="SIFT" />
    </root>

</configuration>
```

---

## 4. Log File Storage & Directory Structure

Log files are stored dynamically under `backend/logs/sessions/`:

```text
backend/
 └── logs/
      └── sessions/
           ├── system.log                <-- Background server logs & default fallback
           ├── user_builder_01.log       <-- Isolated log file for User 1
           ├── user_builder_02.log       <-- Isolated log file for User 2
           └── candidate_interview_42.log <-- Isolated log file for User 3
```

### Example Log Output (`logs/sessions/user_builder_01.log`)
```text
2026-07-30 13:51:22.104 [nio-8080-exec-1] INFO  - WebSocket connection established for user: user_builder_01 (Role: FORM_BUILDER, Session ID: s-101)
2026-07-30 13:51:22.115 [nio-8080-exec-1] INFO  - Opening Gemini Live WSS tunnel for user: user_builder_01 (Role: FORM_BUILDER, FormId: form-99)
2026-07-30 13:51:22.340 [nio-8080-exec-2] INFO  - Outbound WebSocket connection to Google Gemini Live established for user: user_builder_01
2026-07-30 13:51:22.342 [nio-8080-exec-2] INFO  - [SENDING SETUP TO GEMINI LIVE]: {"setup":{"model":"models/gemini-2.0-flash-exp",...}}
2026-07-30 13:51:22.501 [nio-8080-exec-3] INFO  - ✅ Google Gemini Live Setup Complete for user: user_builder_01
2026-07-30 13:51:25.112 [nio-8080-exec-4] INFO  - [Candidate Transcribed Text]: Add a text input field for Full Name
2026-07-30 13:51:25.890 [nio-8080-exec-5] INFO  - Gemini Live issued toolCall 'modifyFormLayout' (id: call-12)
2026-07-30 13:51:25.895 [nio-8080-exec-5] INFO  - [SENT TOOL RESPONSE TO GEMINI LIVE]: {"toolResponse":{"functionResponses":[...]}}
```
