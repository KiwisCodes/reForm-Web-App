"use client";

import React, { useState, useEffect, useRef } from "react";

type ConnectionStatus = "DISCONNECTED" | "CONNECTING" | "CONNECTED" | "INTERRUPTED" | "ERROR";

interface TranscriptMessage {
  id: string;
  sender: "USER" | "AI" | "SYSTEM" | "TOOL";
  text: string;
  timestamp: string;
}

export default function Mode3TesterPage() {
  const [status, setStatus] = useState<ConnectionStatus>("DISCONNECTED");
  const [wsUrl, setWsUrl] = useState<string>("ws://localhost:8080/ws/v1/voice?token=test_token&mode=MODE_3");
  const [isMicActive, setIsMicActive] = useState<boolean>(false);
  const [textInput, setTextInput] = useState<string>("");
  const [transcripts, setTranscripts] = useState<TranscriptMessage[]>([]);
  const [logs, setLogs] = useState<string[]>([]);
  const [isAudioUnlocked, setIsAudioUnlocked] = useState<boolean>(false);
  const [volume, setVolume] = useState<number>(1.0);
  const [activeTab, setActiveTab] = useState<"transcripts" | "logs">("transcripts");

  // BARGE-IN INTERRUPTION STATE
  const [isAiSpeaking, setIsAiSpeaking] = useState<boolean>(false);
  const isAiSpeakingRef = useRef<boolean>(false);

  // References
  const wsRef = useRef<WebSocket | null>(null);
  const transcriptContainerRef = useRef<HTMLDivElement | null>(null);

  // Audio Input (Microphone 16kHz)
  const micAudioContextRef = useRef<AudioContext | null>(null);
  const mediaStreamRef = useRef<MediaStream | null>(null);
  const scriptProcessorRef = useRef<ScriptProcessorNode | null>(null);

  // Audio Output (Speaker Playback 24kHz)
  const playbackAudioContextRef = useRef<AudioContext | null>(null);
  const gainNodeRef = useRef<GainNode | null>(null);
  const nextStartTimeRef = useRef<number>(0);
  const activeSourcesRef = useRef<AudioBufferSourceNode[]>([]);

  // Auto-scroll to bottom when transcripts update
  useEffect(() => {
    if (transcriptContainerRef.current) {
      transcriptContainerRef.current.scrollTop = transcriptContainerRef.current.scrollHeight;
    }
  }, [transcripts]);

  const addLog = (msg: string) => {
    const time = new Date().toLocaleTimeString();
    setLogs((prev) => [`[${time}] ${msg}`, ...prev.slice(0, 99)]);
  };

  const appendTranscriptChunk = (sender: "USER" | "AI" | "SYSTEM" | "TOOL", textChunk: string) => {
    if (!textChunk || !textChunk.trim()) return;
    const time = new Date().toLocaleTimeString();
    const cleanChunk = textChunk.trim();

    setTranscripts((prev) => {
      if (sender === "SYSTEM" || sender === "TOOL") {
        return [
          ...prev,
          { id: `${Date.now()}-${Math.random()}`, sender, text: cleanChunk, timestamp: time },
        ];
      }

      const lastItem = prev[prev.length - 1];
      if (lastItem && lastItem.sender === sender) {
        if (lastItem.text.includes(cleanChunk)) return prev;

        let updatedText = lastItem.text;
        if (!updatedText.endsWith(" ") && !cleanChunk.startsWith(" ") && !/^[.,!?;:]/.test(cleanChunk)) {
          updatedText += " ";
        }
        updatedText += cleanChunk;

        const updatedList = [...prev];
        updatedList[updatedList.length - 1] = {
          ...lastItem,
          text: updatedText,
          timestamp: time,
        };
        return updatedList;
      }

      return [
        ...prev,
        { id: `${Date.now()}-${Math.random()}`, sender, text: cleanChunk, timestamp: time },
      ];
    });
  };

  // -------------------------------------------------------------
  // AUDIO PLAYBACK ENGINE (24kHz Cartesia PCM -> Speaker)
  // -------------------------------------------------------------
  const initPlaybackAudioContext = async () => {
    try {
      if (!playbackAudioContextRef.current) {
        const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
        const ctx = new AudioCtx({ sampleRate: 24000 });
        const gainNode = ctx.createGain();
        gainNode.gain.value = volume;
        gainNode.connect(ctx.destination);

        playbackAudioContextRef.current = ctx;
        gainNodeRef.current = gainNode;
      }

      const ctx = playbackAudioContextRef.current;
      if (ctx.state === "suspended") {
        await ctx.resume();
      }

      setIsAudioUnlocked(ctx.state === "running");
      return ctx;
    } catch (e: any) {
      addLog(`Failed to initialize AudioContext: ${e.message}`);
      return null;
    }
  };

  const playPcm16Chunk = async (rawBuffer: ArrayBuffer) => {
    const ctx = await initPlaybackAudioContext();
    if (!ctx) return;

    try {
      const int16Array = new Int16Array(rawBuffer);
      if (int16Array.length === 0) return;

      const float32Array = new Float32Array(int16Array.length);
      for (let i = 0; i < int16Array.length; i++) {
        float32Array[i] = int16Array[i] / 32768.0;
      }

      const audioBuffer = ctx.createBuffer(1, float32Array.length, 24000);
      audioBuffer.getChannelData(0).set(float32Array);

      const source = ctx.createBufferSource();
      source.buffer = audioBuffer;

      if (gainNodeRef.current) {
        source.connect(gainNodeRef.current);
      } else {
        source.connect(ctx.destination);
      }

      const currentTime = ctx.currentTime;
      if (nextStartTimeRef.current < currentTime) {
        nextStartTimeRef.current = currentTime + 0.04;
      }

      source.start(nextStartTimeRef.current);
      nextStartTimeRef.current += audioBuffer.duration;

      isAiSpeakingRef.current = true;
      setIsAiSpeaking(true);

      activeSourcesRef.current.push(source);
      source.onended = () => {
        activeSourcesRef.current = activeSourcesRef.current.filter((s) => s !== source);
        if (activeSourcesRef.current.length === 0) {
          setTimeout(() => {
            if (activeSourcesRef.current.length === 0) {
              isAiSpeakingRef.current = false;
              setIsAiSpeaking(false);
              addLog("🔊 Cartesia TTS playback finished.");
            }
          }, 300);
        }
      };
    } catch (err: any) {
      addLog(`Audio Playback Error: ${err.message}`);
    }
  };

  const stopAllAudioPlayback = () => {
    activeSourcesRef.current.forEach((source) => {
      try {
        source.stop();
        source.disconnect();
      } catch (e) {}
    });
    activeSourcesRef.current = [];
    if (playbackAudioContextRef.current) {
      nextStartTimeRef.current = playbackAudioContextRef.current.currentTime;
    }
    isAiSpeakingRef.current = false;
    setIsAiSpeaking(false);
    addLog("🛑 Audio playback stopped via Barge-in FLUSH.");
  };

  // -------------------------------------------------------------
  // WEBSOCKET HANDLERS
  // -------------------------------------------------------------
  const connectWebSocket = async () => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      addLog("Already connected.");
      return;
    }

    await initPlaybackAudioContext();

    setStatus("CONNECTING");
    addLog(`Connecting to Mode 3 endpoint: ${wsUrl}`);

    try {
      const ws = new WebSocket(wsUrl);
      wsRef.current = ws;
      ws.binaryType = "arraybuffer";

      ws.onopen = () => {
        setStatus("CONNECTED");
        addLog("✅ Mode 3 WebSocket Connected! (Deepgram STT -> Gemini Flash -> Cartesia TTS)");
        appendTranscriptChunk("SYSTEM", "Mode 3 Cascaded Voice Session Established.");
      };

      ws.onmessage = async (event: MessageEvent) => {
        if (event.data instanceof ArrayBuffer) {
          addLog(`🔊 Received ${event.data.byteLength} PCM bytes from Cartesia TTS`);
          await playPcm16Chunk(event.data);
          return;
        }

        if (typeof event.data === "string") {
          try {
            const data = JSON.parse(event.data);

            if (data.type === "TRANSCRIPT_USER") {
              appendTranscriptChunk("USER", data.text);
            } else if (data.type === "TRANSCRIPT_AI") {
              appendTranscriptChunk("AI", data.text);
            } else if (data.type === "FLUSH_AUDIO_BUFFER" || data.type === "INTERRUPTED") {
              setStatus("INTERRUPTED");
              stopAllAudioPlayback();
              appendTranscriptChunk("SYSTEM", "⚡ Barge-in Interruption: AI speech flushed.");
              setTimeout(() => setStatus("CONNECTED"), 1500);
            } else if (data.type === "TOOL_CALL") {
              appendTranscriptChunk("TOOL", `Tool Executed: ${data.name}`);
            }
          } catch (e) {
            addLog(`Raw Text Frame: ${event.data}`);
          }
        }
      };

      ws.onerror = (err) => {
        addLog("WebSocket Error occurred.");
        setStatus("ERROR");
      };

      ws.onclose = (event) => {
        setStatus("DISCONNECTED");
        stopMicrophone();
        stopAllAudioPlayback();
        addLog(`WebSocket closed (Code: ${event.code}, Reason: ${event.reason || "Clean close"})`);
      };
    } catch (e: any) {
      setStatus("ERROR");
      addLog(`Connection exception: ${e.message}`);
    }
  };

  const disconnectWebSocket = () => {
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }
    stopMicrophone();
    stopAllAudioPlayback();
    setStatus("DISCONNECTED");
    addLog("Disconnected manually.");
  };

  // -------------------------------------------------------------
  // MICROPHONE CAPTURE (16kHz PCM Mono Stream)
  // -------------------------------------------------------------
  const startMicrophone = async () => {
    if (isMicActive) return;

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: 1,
          sampleRate: 16000,
          echoCancellation: true,
          noiseSuppression: true,
        },
      });

      mediaStreamRef.current = stream;

      const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
      const ctx = new AudioCtx({ sampleRate: 16000 });
      micAudioContextRef.current = ctx;

      const source = ctx.createMediaStreamSource(stream);
      const processor = ctx.createScriptProcessor(2048, 1, 1);
      scriptProcessorRef.current = processor;

      processor.onaudioprocess = (e) => {
        if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) return;

        const inputData = e.inputBuffer.getChannelData(0);
        const pcm16 = new Int16Array(inputData.length);
        for (let i = 0; i < inputData.length; i++) {
          const s = Math.max(-1, Math.min(1, inputData[i]));
          pcm16[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
        }

        wsRef.current.send(pcm16.buffer);
      };

      source.connect(processor);
      processor.connect(ctx.destination);

      setIsMicActive(true);
      addLog("🎙️ Microphone started streaming 16kHz PCM to Deepgram STT.");
    } catch (err: any) {
      addLog(`Failed to start microphone: ${err.message}`);
    }
  };

  const stopMicrophone = () => {
    if (scriptProcessorRef.current) {
      scriptProcessorRef.current.disconnect();
      scriptProcessorRef.current = null;
    }
    if (micAudioContextRef.current) {
      micAudioContextRef.current.close();
      micAudioContextRef.current = null;
    }
    if (mediaStreamRef.current) {
      mediaStreamRef.current.getTracks().forEach((track) => track.stop());
      mediaStreamRef.current = null;
    }
    setIsMicActive(false);
    addLog("🔇 Microphone stopped.");
  };

  const handleSendText = () => {
    if (!textInput.trim()) return;
    if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) {
      addLog("Cannot send text: WebSocket is not open.");
      return;
    }

    const payload = JSON.stringify({ text: textInput });
    wsRef.current.send(payload);
    appendTranscriptChunk("USER", textInput);
    addLog(`Sent text input: "${textInput}"`);
    setTextInput("");
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans">
      {/* Header Banner */}
      <header className="border-b border-slate-800 bg-slate-900/60 backdrop-blur px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="h-9 w-9 rounded-lg bg-indigo-600 flex items-center justify-center font-bold text-white shadow-lg shadow-indigo-500/30">
            M3
          </div>
          <div>
            <h1 className="text-xl font-semibold tracking-tight text-white">reForm Mode 3 Voice Tester</h1>
            <p className="text-xs text-slate-400">Deepgram Nova-3 STT → Gemini 3.6 Flash LLM → Cartesia Sonic TTS</p>
          </div>
        </div>

        {/* Status Pills */}
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2 bg-slate-800/80 px-3 py-1.5 rounded-full border border-slate-700 text-xs">
            <span className={`h-2.5 w-2.5 rounded-full ${status === "CONNECTED" ? "bg-emerald-400 animate-pulse" : status === "CONNECTING" ? "bg-amber-400 animate-ping" : status === "INTERRUPTED" ? "bg-purple-400" : "bg-slate-500"}`} />
            <span className="font-mono text-slate-200">{status}</span>
          </div>

          <div className={`px-3 py-1.5 rounded-full text-xs font-medium ${isAiSpeaking ? "bg-purple-500/20 text-purple-300 border border-purple-500/30 animate-pulse" : "bg-slate-800 text-slate-400"}`}>
            {isAiSpeaking ? "🔊 AI Speaking" : "🔇 AI Idle"}
          </div>
        </div>
      </header>

      {/* Main Content Split Panel */}
      <div className="flex-1 flex flex-col md:flex-row overflow-hidden">
        {/* Left Column: Control Panel */}
        <div className="w-full md:w-80 border-r border-slate-800 bg-slate-900/30 p-5 flex flex-col gap-6 overflow-y-auto">
          {/* WebSocket Connection Section */}
          <div className="space-y-3">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-400">Connection Settings</h2>
            <div className="space-y-2">
              <label className="text-xs text-slate-400">WebSocket URL</label>
              <input
                type="text"
                value={wsUrl}
                onChange={(e) => setWsUrl(e.target.value)}
                disabled={status === "CONNECTED" || status === "CONNECTING"}
                className="w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs font-mono text-slate-200 focus:outline-none focus:border-indigo-500 disabled:opacity-50"
              />
            </div>

            {status === "DISCONNECTED" || status === "ERROR" ? (
              <button
                onClick={connectWebSocket}
                className="w-full bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-medium py-2.5 rounded-lg shadow-lg shadow-indigo-600/20 transition"
              >
                Connect WebSocket
              </button>
            ) : (
              <button
                onClick={disconnectWebSocket}
                className="w-full bg-rose-600 hover:bg-rose-500 text-white text-xs font-medium py-2.5 rounded-lg transition"
              >
                Disconnect
              </button>
            )}
          </div>

          {/* Microphone & Audio Controls */}
          <div className="space-y-3 pt-4 border-t border-slate-800/80">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-400">Voice Controls</h2>
            
            <button
              onClick={isMicActive ? stopMicrophone : startMicrophone}
              disabled={status !== "CONNECTED"}
              className={`w-full text-xs font-medium py-3 rounded-lg flex items-center justify-center gap-2 transition ${isMicActive ? "bg-rose-500/20 text-rose-300 border border-rose-500/40 hover:bg-rose-500/30" : "bg-emerald-600 hover:bg-emerald-500 text-white shadow-lg shadow-emerald-600/20"} disabled:opacity-40`}
            >
              <span>{isMicActive ? "⏹ Stop Microphone" : "🎙️ Start Microphone (16kHz)"}</span>
            </button>

            <button
              onClick={stopAllAudioPlayback}
              disabled={!isAiSpeaking}
              className="w-full bg-purple-600/30 hover:bg-purple-600/50 text-purple-200 border border-purple-500/30 text-xs font-medium py-2 rounded-lg transition disabled:opacity-30"
            >
              ⚡ Test Manual Barge-in Flush
            </button>
          </div>

          {/* Text Input Drawer (Bypasses STT) */}
          <div className="space-y-3 pt-4 border-t border-slate-800/80">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-400">Text Fallback (Bypass STT)</h2>
            <div className="flex gap-2">
              <input
                type="text"
                value={textInput}
                onChange={(e) => setTextInput(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && handleSendText()}
                placeholder="Type message to LLM..."
                disabled={status !== "CONNECTED"}
                className="flex-1 bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-indigo-500 disabled:opacity-50"
              />
              <button
                onClick={handleSendText}
                disabled={status !== "CONNECTED" || !textInput.trim()}
                className="bg-indigo-600 hover:bg-indigo-500 text-white text-xs px-3 rounded-lg disabled:opacity-40 transition"
              >
                Send
              </button>
            </div>
          </div>
        </div>

        {/* Right Column: Transcripts & Event Logs */}
        <div className="flex-1 flex flex-col bg-slate-950">
          {/* Tab Selection Bar */}
          <div className="border-b border-slate-800 bg-slate-900/40 px-6 py-2 flex items-center justify-between">
            <div className="flex gap-4 text-xs font-medium">
              <button
                onClick={() => setActiveTab("transcripts")}
                className={`pb-2 pt-1 border-b-2 transition ${activeTab === "transcripts" ? "border-indigo-500 text-indigo-400" : "border-transparent text-slate-400 hover:text-slate-200"}`}
              >
                Live Transcripts ({transcripts.length})
              </button>
              <button
                onClick={() => setActiveTab("logs")}
                className={`pb-2 pt-1 border-b-2 transition ${activeTab === "logs" ? "border-indigo-500 text-indigo-400" : "border-transparent text-slate-400 hover:text-slate-200"}`}
              >
                Debug Logs ({logs.length})
              </button>
            </div>

            <button
              onClick={() => { setTranscripts([]); setLogs([]); }}
              className="text-xs text-slate-500 hover:text-slate-300"
            >
              Clear
            </button>
          </div>

          {/* Transcripts Display Area */}
          {activeTab === "transcripts" ? (
            <div ref={transcriptContainerRef} className="flex-1 p-6 overflow-y-auto space-y-4">
              {transcripts.length === 0 ? (
                <div className="h-full flex items-center justify-center text-slate-600 text-xs italic">
                  No transcripts yet. Connect and speak into your microphone or send text.
                </div>
              ) : (
                transcripts.map((msg) => (
                  <div
                    key={msg.id}
                    className={`flex flex-col ${msg.sender === "USER" ? "items-end" : msg.sender === "AI" ? "items-start" : "items-center"}`}
                  >
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-[10px] uppercase font-semibold text-slate-500">{msg.sender}</span>
                      <span className="text-[10px] text-slate-600">{msg.timestamp}</span>
                    </div>

                    <div
                      className={`max-w-xl rounded-2xl px-4 py-3 text-sm leading-relaxed ${
                        msg.sender === "USER"
                          ? "bg-indigo-600 text-white rounded-br-none shadow-md shadow-indigo-600/10"
                          : msg.sender === "AI"
                          ? "bg-slate-800 text-slate-100 border border-slate-700/60 rounded-bl-none"
                          : msg.sender === "TOOL"
                          ? "bg-amber-500/10 text-amber-300 border border-amber-500/20 text-xs font-mono"
                          : "bg-slate-900 text-slate-400 text-xs border border-slate-800"
                      }`}
                    >
                      {msg.text}
                    </div>
                  </div>
                ))
              )}
            </div>
          ) : (
            /* Event Logs Area */
            <div className="flex-1 p-4 bg-slate-950 font-mono text-xs overflow-y-auto space-y-1 text-slate-400">
              {logs.map((logStr, i) => (
                <div key={i} className="border-b border-slate-900 pb-1">{logStr}</div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
