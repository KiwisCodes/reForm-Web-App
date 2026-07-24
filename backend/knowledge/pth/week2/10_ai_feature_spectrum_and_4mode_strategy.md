# 10. AI Feature Spectrum & 4-Mode Architectural Strategy
**Document Version:** 1.0  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  
**Author:** Senior Technical Lead & AI System Architect  

---

## 1. Executive Summary & System Vision

The **reForm** platform is an omni-modal form creation and candidate evaluation system. It bridges traditional static web forms with real-time, conversational AI experiences.

To serve diverse form builder requirements—ranging from zero-cost static surveys to high-throughput recruiter text chatbots and sub-second live voice recruiter interviews—reForm implements a **4-Mode Operational Spectrum**.

---

## 2. The 4-Mode Operational Spectrum

```text
  MODE 1: Manual Form (Static Drag-and-Drop Canvas / Standard Inputs over HTTP)
  MODE 2: Text Chat Form (Interactive Chatbot Co-Builder & Interviewer over HTTP REST)
  MODE 3: Voice Real-Time Cascaded (Deepgram STT → Gemini 3.6 Flash → Cartesia TTS over WSS)
  MODE 4: Voice Real-Time Native Live (Gemini 3.1 Flash Live Native Audio-to-Audio over WSS)
```

---

## 3. Comprehensive Modes Comparison Matrix

| Dimension | Mode 1: Manual Form | Mode 2: Text Chat Form | Mode 3: Voice Cascaded Pipeline | Mode 4: Voice Native Live |
| :--- | :--- | :--- | :--- | :--- |
| **Primary Transport** | HTTP REST (Stateless) | HTTP REST / SSE | Raw WebSocket (WSS) | Raw WebSocket (WSS) |
| **Target Engine** | Standard React Form Canvas | **Gemini 3.6 Flash** | Deepgram + Flash 3.6 + Cartesia | **Gemini 3.1 Flash Live** (`models/gemini-3.1-flash-live-preview`) |
| **Cost / Minute** | **$0.00** | **~$0.002** (~0.2¢/min) | **~$0.0176** (~1.8¢/min) | **~$0.027** (~2.7¢/min) |
| **Latency** | N/A (Client-side) | ~400ms | ~700ms – 900ms | **~300ms** (Sub-second natural pace) |
| **Acoustic Nuance** | None | None | None (Stripped by STT) | **Native** (Tone, pitch, emotion, hesitation) |
| **Barge-in Support** | N/A | N/A | Manual Interruption Code | **Native** (Auto-detects speech & flushes) |
| **Best Used For** | Basic survey forms | Structured text chat & schema gen | Budget-friendly voice surveys | Premium recruiter interviews & Co-Builder assistant |

---

## 4. Deep-Dive Strategy per Mode

### Mode 1: Manual Form (Static Canvas)
* **Transport:** Standard HTTP REST endpoints (`/api/v1/forms`).
* **Execution:** Traditional React input fields (text, radio, checkboxes). Zero AI invocation required.
* **Cost:** $0.00 per submission.

### Mode 2: Text Chat Form (Interactive LLM Chatbot)
* **Transport:** HTTP REST / Server-Sent Events (SSE).
* **Execution:** Driven by **Gemini 3.6 Flash**. The candidate or form builder interacts via text.
* **Capabilities:** Generates valid form block DTOs, validates candidate text answers, and returns structured JSON responses (~400ms latency).

### Mode 3: Voice Real-Time Cascaded Pipeline
* **Transport:** Raw WebSockets (`/ws/v1/voice`).
* **Execution:** Microservice pipeline combining 3 specialized engines:
  1. **STT:** Deepgram Nova-3 Streaming WSS (<150ms).
  2. **LLM:** Gemini 3.6 Flash Stateless HTTP REST (~200ms).
  3. **TTS:** Cartesia Sonic Streaming WSS (~90ms).
* **Interruption Handling:** Manual interruption code. Deepgram speech detection (`speech_started`) triggers Cartesia cancellation frames (`{"cancel": true}`) and client buffer flushes (`FLUSH_AUDIO_BUFFER`).
* **Cost:** ~$0.01765 / minute (~1.8¢/min).

### Mode 4: Voice Real-Time Native Live
* **Transport:** Raw WebSockets (`/ws/v1/voice`).
* **Execution:** Direct outbound WSS connection to Google Gemini 3.1 Multimodal Live API (`models/gemini-3.1-flash-live-preview`).
* **Capabilities:** Native audio-to-audio processing (~300ms latency), native barge-in speech detection, acoustic nuance capture (tone, emotion, pitch), and parallel tool calling (`modifyFormLayout`).
* **Cost:** ~$0.027 / minute (~2.7¢/min).

---

## 5. Mode Selection Decision Tree

```text
                                  [ Form Creator Selection ]
                                              │
                    ┌─────────────────────────┼─────────────────────────┐
                    ▼                         ▼                         ▼
            [ Standard Survey? ]      [ Text Assistance? ]     [ Real-Time Voice? ]
                    │                         │                         │
                    ▼                         ▼                         │
                 MODE 1                    MODE 2                       │
              (Manual Form)             (Text Chatbot)                  │
                                                                        ▼
                                                       ┌────────────────┴────────────────┐
                                                       ▼                                 ▼
                                             [ Budget Priority? ]              [ Quality & Speed? ]
                                                       │                                 │
                                                       ▼                                 ▼
                                                    MODE 3                            MODE 4
                                              (Cascaded Pipeline)               (Native Audio Live)
```
