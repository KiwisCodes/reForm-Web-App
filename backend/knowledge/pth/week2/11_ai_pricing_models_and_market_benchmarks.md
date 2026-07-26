# 11. AI Pricing Models & Market Benchmarks (2026 Update)
**Document Version:** 1.0  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  
**Author:** Senior Technical Lead & AI System Architect  

---

## 1. Gemini 3.6 Flash Model Card & Benchmarks (March 2026 Model Release)

Gemini 3.6 Flash is reForm's primary text-reasoning, layout generation, and candidate evaluation engine across Modes 2, 3, and post-session processing.

### Official Token Rates:
* **Text Input Price:** **$1.50** / 1M tokens
* **Text Output Price:** **$7.50** / 1M tokens

### Performance Benchmark Matrix:
* **SWE-Bench Pro (Public):** **58.7%**
* **DeepSWE v1.1:** **49%**
* **Terminal-bench 2.1:** **78.0%**
* **MLE-Bench:** **63.9%**
* **GDPVal-AA v2 (Elo Rating):** **1421**
* **CharXiv Reasoning (No Tools):** **83.0%**
* **CharXiv Reasoning (With Tools):** **85.2%**
* **GDM-MRCR v2 (128k avg):** **89.4%**
* **GDM-MRCR v2 (1M pointwise):** **91.8%**

---

## 2. Mode 4 Pricing: Native Audio-to-Audio (`models/gemini-3.1-flash-live-preview`)

### Official Token Rates:
* **Audio Input:** **$3.00** / 1M tokens
* **Audio Output:** **$12.00** / 1M tokens
* **Text Input:** **$0.75** / 1M tokens
* **Text Output:** **$4.50** / 1M tokens

### Real-Time Audio Streaming Calculation:
Continuous audio streams at ~25 to 30 tokens per second (~1,800 tokens per minute):

```text
  1 Minute Audio Input (Mic Streaming @ ~1,800 tokens/min):
  1,800 tokens × ($3.00 / 1,000,000) = $0.0054 / minute

  1 Minute Audio Output (AI Speaking Out Loud @ ~1,800 tokens/min):
  1,800 tokens × ($12.00 / 1,000,000) = $0.0216 / minute

  Combined 1-Minute Live Conversation Baseline (40% Input / 40% Output / 20% Silence):
  $0.0054 (Input) + $0.0216 (Output) = $0.027 / minute (~2.7¢ / minute)
```

### Session Cost Projections:
* **10-Minute Session:** Approx. **$0.27**
* **20-Minute Session:** **$0.24 to $0.54** (Standard baseline ~$0.24 for 12 min input + 8 min output)
* **30-Minute Session:** Approx. **$0.36**

---

## 3. Mode 3 Market Comparison (2026 Voice AI Providers)

To determine the optimal stack for Mode 3 (Voice Cascaded), we evaluated 2026 pricing across leading STT, LLM, and TTS providers:

| Layer | Provider & Model | 2026 Billing Rate | Latency | Selection Rationale |
| :--- | :--- | :--- | :--- | :--- |
| **STT (Speech-to-Text)** | **Deepgram Nova-3 (Streaming)** | **$0.0077 / minute** | <150ms | **SELECTED:** Gold standard accuracy under noise & lowest latency. |
| | AssemblyAI Universal Stream | $0.0025 / min (Base) | ~300ms | Cheaper base rate, but add-ons (Speaker ID) push cost to $0.005/min. |
| | OpenAI Whisper API | $0.0060 / minute | ~500ms | High accuracy, but chunk buffering adds latency to voice stream. |
| **LLM (Reasoning)** | **Gemini 3.6 Flash** | **$1.50 in / $7.50 out** (~$0.00195/min) | ~200ms | **SELECTED:** Superior reasoning (78% Terminal-bench, 1421 Elo) for schema checks. |
| | GPT-4o-mini | $0.15 in / $0.60 out (~$0.0002/min) | ~250ms | Extremely cheap, lower reasoning scores on complex tool calling. |
| **TTS (Text-to-Speech)** | **Cartesia Sonic** | **~$0.0080 / minute** (~800 chars) | **~90ms** | **SELECTED:** Fastest TTS engine in industry with natural inflection. |
| | ElevenLabs Multilingual v2 | $0.10 – $0.50 / minute | ~350ms | High audio quality, but prohibitedly expensive for voice agents. |
| | OpenAI TTS Standard | $15.00 / 1M chars (~$0.012/min) | ~250ms | Good quality, but slower latency than Cartesia Sonic. |

### Selected Mode 3 Stack Cost (Deepgram Nova-3 + Gemini 3.6 Flash + Cartesia Sonic):
* **STT:** $0.0077 / min
* **LLM (Gemini 3.6 Flash):** $0.00195 / min
* **TTS (Cartesia Sonic):** $0.0080 / min
* **Total Combined Mode 3 Baseline:** Approx. **$0.01765 / minute** (~1.8¢ per minute, ~35% cheaper than Mode 4).
