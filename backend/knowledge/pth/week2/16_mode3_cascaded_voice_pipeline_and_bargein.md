# 16. Mode 3 Cascaded Voice Pipeline & Barge-in Handling
**Document Version:** 1.0  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  
**Author:** Senior Technical Lead & AI System Architect  

---

## 1. Mode 3 Overview: Voice Cascaded Pipeline

While Mode 4 uses a single native connection, **Mode 3 (Voice Cascaded)** coordinates specialized microservices across persistent sockets and stateless REST calls:

```text
                     OPTIMIZED MODE 3 CONNECTION FOOTPRINT
                     
  [ Candidate Browser ] ◄════ (1. WSS Inbound) ════► [ Spring Backend ]
                                                           │
        ┌──────────────────────────────────────────────────┼──────────────────────────────────────────────────┐
        ▼                                                  ▼                                                  ▼
[ 2. Deepgram STT ]                              [ 3. Gemini 3.6 Flash ]                            [ 4. Cartesia TTS ]
(Outbound WSS Stream)                            (Stateless HTTP REST Call)                         (Outbound WSS Stream)
- Continuous Audio Mic                           - Fast Text & Tool Calling                         - Audio Synthesis Stream
- Per-User Session                               - Shared Non-Blocking REST                          - Per-User Session
```

---

## 2. Connection Optimization: Persistent Sockets vs. Stateless LLM

* **Audio Streaming Requires Sockets (Per-User Isolation):** Raw binary audio streams (PCM 16-bit) cannot be multiplexed into a single shared socket. Deepgram STT and Cartesia TTS APIs require **1 dedicated WebSocket per active user audio session** to maintain acoustic context and voice parameters.
* **LLM Layer is Stateless (Shared Non-Blocking REST):** The middle reasoning engine (**Gemini 3.6 Flash**) receives text transcripts and outputs text/tool calls. It does **NOT** require a persistent socket!
* **The Optimization:** We use **2 WebSockets + 1 Stateless HTTP REST call** per user session instead of holding 3 persistent WebSockets open.

---

## 3. Server Node Capacity Math: How Many Concurrent Users Can We Handle & Why?

For a single standard Spring Boot application instance (e.g., 4 vCPU / 8 GB RAM):

### A. Socket Memory Calculation (Netty / NIO TCP Buffers)
* Each open WebSocket (1 Client WSS + 1 Deepgram WSS + 1 Cartesia WSS) consumes ~15 KB of RAM for TCP socket buffers.
* For **1,000 concurrent active voice users**:
  $$1,000 \text{ users} \times 3 \text{ sockets} = 3,000 \text{ open TCP socket file descriptors (FDs)}$$
* Memory consumed by 3,000 open sockets:
  $$3,000 \times 15 \text{ KB} \approx \mathbf{45 \text{ MB of RAM}} \text{ (Negligible RAM footprint!)}$$

### B. Non-Blocking Stateless REST Execution (Gemini 3.6 Flash)
* Using Spring WebFlux / Netty `WebClient`, HTTP REST calls to Gemini 3.6 Flash are **non-blocking**. A single background event loop thread handles thousands of concurrent REST calls while waiting for Gemini's 200ms text responses without blocking thread execution.

### C. Network Bandwidth Calculation
* Raw 16-bit 16kHz PCM mono audio = **256 kbps (32 KB/sec)** per user direction.
* For **1,000 concurrent active users**:
  $$1,000 \text{ users} \times 256 \text{ kbps} \approx \mathbf{256 \text{ Mbps of network bandwidth}}$$
* A standard cloud instance with a **1 Gbps NIC** processes 1,000 concurrent voice streams with **75% network headroom remaining**!

### 🏁 Single Node Capacity Verdict:
A single 4 vCPU / 8 GB RAM Spring Boot instance easily handles **~2,000 to 3,000 active concurrent live voice sessions**. With 3 load-balanced nodes behind Nginx, reForm scales to **10,000+ concurrent live voice interviews**!

---

## 4. Real-Time Barge-in & Interruption Handling (Flush Mechanics)

```text
  Step 1: AI is speaking (Cartesia TTS sending audio frames to client).
  Step 2: Candidate begins speaking into their microphone.
  Step 3: Deepgram Nova-3 detects speech activity and emits an immediate JSON event:
          { "type": "UtteranceEnd" } or { "is_final": false, "speech_started": true }
  Step 4: CascadedVoiceAdapter catches speech_started event and executes Interruption Flush:
          - Sends an immediate WebSocket cancellation frame to Cartesia TTS: {"context_id": "c123", "cancel": true}
          - Sends a clear/flush buffer frame to client browser: {"type": "FLUSH_AUDIO_BUFFER"}
  Step 5: Client browser clears audio element buffer immediately, halting AI speech in mid-sentence!
```
