"use client";

import React, { useState, useEffect, useRef } from "react";

type ConnectionStatus = "DISCONNECTED" | "CONNECTING" | "CONNECTED" | "INTERRUPTED" | "ERROR";

interface TranscriptMessage {
  id: string;
  sender: "USER" | "AI" | "SYSTEM" | "TOOL";
  text: string;
  timestamp: string;
}

export default function Mode4TesterPage() {
  const [status, setStatus] = useState<ConnectionStatus>("DISCONNECTED");
  const [selectedMode, setSelectedMode] = useState<"MODE_4" | "MODE_3">("MODE_4");
  const [wsUrl, setWsUrl] = useState<string>("ws://localhost:8080/ws/v1/voice?token=test_token&mode=MODE_4");
  const [isMicActive, setIsMicActive] = useState<boolean>(false);
  const [textInput, setTextInput] = useState<string>("");
  const [transcripts, setTranscripts] = useState<TranscriptMessage[]>([]);
  const [logs, setLogs] = useState<string[]>([]);
  const [isAudioUnlocked, setIsAudioUnlocked] = useState<boolean>(false);
  const [volume, setVolume] = useState<number>(1.0);
  const [activeTab, setActiveTab] = useState<"transcripts" | "logs">("transcripts");

  // BARGE-IN TOGGLE
  const [disableBargeIn, setDisableBargeIn] = useState<boolean>(true);
  const disableBargeInRef = useRef<boolean>(true);
  useEffect(() => {
    disableBargeInRef.current = disableBargeIn;
  }, [disableBargeIn]);

  // AI Turn & Speaking State Tracking
  const [isAiSpeaking, setIsAiSpeaking] = useState<boolean>(false);
  const isAiSpeakingRef = useRef<boolean>(false);

  // WebSockets and Audio References
  const wsRef = useRef<WebSocket | null>(null);
  const transcriptContainerRef = useRef<HTMLDivElement | null>(null);
  
  // Audio Input (Microphone - 16kHz)
  const micAudioContextRef = useRef<AudioContext | null>(null);
  const mediaStreamRef = useRef<MediaStream | null>(null);
  const scriptProcessorRef = useRef<ScriptProcessorNode | null>(null);

  // Audio Output (Speaker Playback - 24kHz)
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

  /**
   * HORIZONTAL WORD-BY-WORD STREAMING TRANSCRIPT APPENDER (Duplicate-Free)
   */
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

      // If last message is from the same sender, append to it horizontally (avoiding duplicate exact string repeats)
      if (lastItem && lastItem.sender === sender) {
        if (lastItem.text.includes(cleanChunk)) {
          return prev; // Prevent duplicate text frame duplication
        }

        let updatedText = lastItem.text;
        if (
          updatedText.length > 0 &&
          !updatedText.endsWith(" ") &&
          !cleanChunk.startsWith(" ") &&
          !/^[.,!?;:]/.test(cleanChunk)
        ) {
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

      // Otherwise, start a new sentence bubble for this turn
      return [
        ...prev,
        { id: `${Date.now()}-${Math.random()}`, sender, text: cleanChunk, timestamp: time },
      ];
    });
  };

  // -------------------------------------------------------------
  // AUDIO PLAYBACK ENGINE (24kHz PCM16 -> Web Audio API Queue)
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
              addLog("🔊 AI finished audio playback.");
            }
          }, 400);
        }
      };
    } catch (err: any) {
      addLog(`Audio Playback Error: ${err.message}`);
    }
  };

  const playBase64AudioChunk = async (base64Str: string) => {
    try {
      const binaryString = atob(base64Str);
      const len = binaryString.length;
      const bytes = new Uint8Array(len);
      for (let i = 0; i < len; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }
      await playPcm16Chunk(bytes.buffer);
    } catch (e: any) {
      addLog(`Error decoding Base64 audio chunk: ${e.message}`);
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
  };

  // -------------------------------------------------------------
  // WEBSOCKET HANDLERS & RESPONSE PARSER
  // -------------------------------------------------------------
  const connectWebSocket = async () => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      addLog("Already connected.");
      return;
    }

    await initPlaybackAudioContext();

    setStatus("CONNECTING");
    addLog(`Connecting to: ${wsUrl}`);

    try {
      const ws = new WebSocket(wsUrl);
      wsRef.current = ws;
      ws.binaryType = "arraybuffer";

      ws.onopen = () => {
        setStatus("CONNECTED");
        addLog("✅ WebSocket connected! Gemini Live Mode 4 active.");
        appendTranscriptChunk("SYSTEM", "Connected to Gemini Live Mode 4 (gemini-3.1-flash-live-preview).");
      };

      ws.onmessage = (event) => {
        if (typeof event.data === "string") {
          handleTextMessage(event.data);
        } else if (event.data instanceof ArrayBuffer) {
          addLog(`🔊 Received binary audio payload (${event.data.byteLength} bytes)`);
          playPcm16Chunk(event.data);
        }
      };

      ws.onerror = (err) => {
        addLog(`❌ WebSocket Error`);
        setStatus("ERROR");
      };

      ws.onclose = (evt) => {
        setStatus("DISCONNECTED");
        addLog(`🔌 Connection closed (Code: ${evt.code}, Reason: ${evt.reason || "Clean close"})`);
        stopMicrophone();
      };
    } catch (e: any) {
      setStatus("ERROR");
      addLog(`Connection failed: ${e.message}`);
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
    addLog("Disconnected by user.");
  };

  const handleTextMessage = (dataStr: string) => {
    try {
      if (dataStr === "PONG") {
        addLog("Heartbeat PONG received.");
        return;
      }

      const data = JSON.parse(dataStr);

      if (data.type === "TRANSCRIPT_USER") {
        appendTranscriptChunk("USER", data.text);
        return;
      }
      if (data.type === "TRANSCRIPT_AI") {
        appendTranscriptChunk("AI", data.text);
        return;
      }
      if (data.type === "INTERRUPTED") {
        if (disableBargeInRef.current) {
          addLog("🛡️ [BARGE-IN DISABLED] Ignored INTERRUPTED event.");
          return;
        }
        setStatus("INTERRUPTED");
        addLog("⚡ Barge-in detected. Flushing audio player.");
        stopAllAudioPlayback();
        setTimeout(() => setStatus("CONNECTED"), 1000);
        return;
      }

      if (data.type === "SESSION_ENDED") {
        addLog(`🔴 [SESSION ENDED BY AI] Reason: ${data.reason}. Summary: ${data.summary || "N/A"}`);
        stopMicrophone();
        stopAllAudioPlayback();
        setStatus("DISCONNECTED");
        return;
      }

      if (data.setupComplete) {
        addLog("🏁 Gemini Setup Complete.");
        return;
      }

      if (data.serverContent) {
        const serverContent = data.serverContent;
        const parts = serverContent.modelTurn?.parts;

        // 1. Process inline Base64 Audio
        if (parts && Array.isArray(parts)) {
          for (const part of parts) {
            if (part.inlineData && part.inlineData.data) {
              addLog(`🔊 Received Base64 audio chunk (${part.inlineData.data.length} chars)`);
              playBase64AudioChunk(part.inlineData.data);
            }
          }
        }

        // 2. Extract AI text from outputTranscription or text parts without duplication
        let aiText = "";
        if (serverContent.outputTranscription?.text) {
          aiText = serverContent.outputTranscription.text;
        } else if (parts && Array.isArray(parts)) {
          for (const part of parts) {
            if (part.text) {
              aiText += part.text;
            }
          }
        }

        if (aiText) {
          appendTranscriptChunk("AI", aiText);
        }

        // 3. User Transcription
        if (serverContent.inputTranscription?.text) {
          appendTranscriptChunk("USER", serverContent.inputTranscription.text);
        }

        if (serverContent.turnComplete) {
          addLog("🏁 Gemini Turn Complete.");
        }

        if (serverContent.interrupted) {
          if (disableBargeInRef.current) {
            addLog("🛡️ [BARGE-IN DISABLED] Ignored serverContent.interrupted flag.");
          } else {
            addLog("⚡ Gemini native barge-in detected.");
            stopAllAudioPlayback();
          }
        }
      }

      if (data.toolCall) {
        appendTranscriptChunk("TOOL", `Tool Call Triggered: ${JSON.stringify(data.toolCall)}`);
        addLog(`🛠️ Tool Call: ${JSON.stringify(data.toolCall)}`);
      }
    } catch (e) {
      addLog(`Received text: ${dataStr}`);
    }
  };

  // -------------------------------------------------------------
  // MICROPHONE STREAMING (16kHz PCM16 Input with Audio Lock Guard)
  // -------------------------------------------------------------
  const toggleMicrophone = async () => {
    if (isMicActive) {
      stopMicrophone();
    } else {
      await startMicrophone();
    }
  };

  const startMicrophone = async () => {
    if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) {
      alert("Please connect to WebSocket first!");
      return;
    }

    await initPlaybackAudioContext();

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          sampleRate: 16000,
          channelCount: 1,
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      });
      mediaStreamRef.current = stream;

      const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
      const audioCtx = new AudioCtx({ sampleRate: 16000 });
      micAudioContextRef.current = audioCtx;

      const source = audioCtx.createMediaStreamSource(stream);
      const processor = audioCtx.createScriptProcessor(2048, 1, 1);
      scriptProcessorRef.current = processor;

      processor.onaudioprocess = (e) => {
        if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) return;

        // Pause sending mic audio frames to WebSocket while AI is speaking
        if (disableBargeInRef.current && isAiSpeakingRef.current) {
          return;
        }

        const inputBuffer = e.inputBuffer.getChannelData(0);
        const pcm16 = new Int16Array(inputBuffer.length);
        for (let i = 0; i < inputBuffer.length; i++) {
          const sample = Math.max(-1, Math.min(1, inputBuffer[i]));
          pcm16[i] = sample < 0 ? sample * 0x8000 : sample * 0x7fff;
        }

        wsRef.current.send(pcm16.buffer);
      };

      source.connect(processor);
      processor.connect(audioCtx.destination);

      setIsMicActive(true);
      addLog("🎤 Microphone streaming started (16kHz 16-bit PCM).");
    } catch (e: any) {
      addLog(`❌ Failed to start microphone: ${e.message}`);
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
    addLog("🛑 Microphone stopped.");
  };

  // -------------------------------------------------------------
  // TEXT MESSAGE SENDER
  // -------------------------------------------------------------
  const handleSendText = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!textInput.trim()) return;

    if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) {
      alert("WebSocket is not connected.");
      return;
    }

    await initPlaybackAudioContext();

    const payload = JSON.stringify({ type: "TEXT", text: textInput });
    wsRef.current.send(payload);
    appendTranscriptChunk("USER", textInput);
    addLog(`Sent text input: "${textInput}"`);
    setTextInput("");
  };

  const handleVolumeChange = (newVol: number) => {
    setVolume(newVol);
    if (gainNodeRef.current) {
      gainNodeRef.current.gain.value = newVol;
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans p-4 sm:p-6">
      {/* UNLOCK AUDIO BANNER */}
      {!isAudioUnlocked && (
        <div className="max-w-6xl w-full mx-auto mb-4 p-3 bg-amber-500/20 border border-amber-500/40 rounded-xl flex items-center justify-between">
          <div className="flex items-center gap-2 text-xs text-amber-200">
            <span>⚠️ Speaker output is currently suspended by your browser.</span>
          </div>
          <button
            onClick={initPlaybackAudioContext}
            className="px-3 py-1 bg-amber-500 hover:bg-amber-400 text-slate-950 text-xs font-bold rounded-lg transition"
          >
            🔊 Click to Enable Sound
          </button>
        </div>
      )}

      {/* HEADER */}
      <header className="max-w-6xl w-full mx-auto flex flex-col md:flex-row items-center justify-between gap-4 pb-6 border-b border-slate-800">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold bg-gradient-to-r from-blue-400 via-indigo-300 to-purple-400 bg-clip-text text-transparent">
              reForm AI Voice & Chat Tester
            </h1>
            <span className={`px-2.5 py-0.5 text-xs font-semibold rounded-full border ${
              selectedMode === "MODE_4"
                ? "bg-blue-500/20 text-blue-300 border-blue-500/30"
                : selectedMode === "MODE_3"
                ? "bg-purple-500/20 text-purple-300 border-purple-500/30"
                : "bg-pink-500/20 text-pink-300 border-pink-500/30"
            }`}>
              {selectedMode === "MODE_4" && "⚡ Mode 4: Gemini 3.1 Flash Live"}
              {selectedMode === "MODE_3" && "🎙️ Mode 3: Deepgram + Cartesia"}
              {(selectedMode as string) === "MODE_2" && "💬 Mode 2: Gemini 3.6 Flash REST"}
            </span>
          </div>
          <p className="text-sm text-slate-400 mt-1">
            Real-Time Conversational AI Strategy Tester (Mode 2 / Mode 3 / Mode 4)
          </p>
        </div>

        {/* CONTROLS & STATUS */}
        <div className="flex items-center gap-4">
          <button
            onClick={() => setDisableBargeIn(!disableBargeIn)}
            className={`px-3 py-1.5 rounded-full text-xs font-semibold border transition ${
              disableBargeIn
                ? "bg-indigo-500/20 text-indigo-300 border-indigo-500/40"
                : "bg-slate-800 text-slate-400 border-slate-700 hover:text-slate-200"
            }`}
          >
            🛡️ Barge-In: {disableBargeIn ? "DISABLED (No Cuts)" : "ENABLED"}
          </button>

          <div className="flex items-center gap-2 bg-slate-900 border border-slate-800 px-3 py-1.5 rounded-full text-xs">
            <span className="text-slate-400">Vol:</span>
            <input
              type="range"
              min="0"
              max="1"
              step="0.05"
              value={volume}
              onChange={(e) => handleVolumeChange(parseFloat(e.target.value))}
              className="w-16 accent-indigo-500 cursor-pointer"
            />
          </div>

          <div
            className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-semibold border ${
              status === "CONNECTED"
                ? "bg-emerald-500/10 text-emerald-400 border-emerald-500/30"
                : status === "CONNECTING"
                ? "bg-amber-500/10 text-amber-400 border-amber-500/30"
                : status === "INTERRUPTED"
                ? "bg-purple-500/20 text-purple-300 border-purple-500/40 animate-pulse"
                : "bg-red-500/10 text-red-400 border-red-500/30"
            }`}
          >
            <span
              className={`w-2 h-2 rounded-full ${
                status === "CONNECTED"
                  ? "bg-emerald-400 animate-ping"
                  : status === "CONNECTING"
                  ? "bg-amber-400 animate-pulse"
                  : status === "INTERRUPTED"
                  ? "bg-purple-400"
                  : "bg-red-400"
              }`}
            />
            {status}
          </div>
        </div>
      </header>

      {/* MAIN LAYOUT */}
      <main className="max-w-6xl w-full mx-auto grid grid-cols-1 lg:grid-cols-3 gap-6 mt-6 flex-1">
        {/* LEFT PANEL */}
        <div className="flex flex-col gap-6">
          <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-5 backdrop-blur-md shadow-xl">
            <h2 className="text-xs font-bold text-slate-300 uppercase tracking-wider mb-3">
              1. Connection Endpoint
            </h2>
            <div className="space-y-3">
              <div>
                <label className="text-[11px] font-semibold text-slate-300 block mb-2">
                  Select AI Architecture Mode
                </label>
                <div className="grid grid-cols-3 gap-1.5 p-1 bg-slate-950 border border-slate-800 rounded-xl mb-3">
                  <button
                    type="button"
                    onClick={() => {
                      setSelectedMode("MODE_4");
                      setWsUrl(`ws://localhost:8080/ws/v1/voice?token=test_token&mode=MODE_4&formId=11111111-1111-1111-1111-111111111111`);
                    }}
                    className={`py-2 px-2 text-[11px] font-bold rounded-lg transition ${
                      selectedMode === "MODE_4"
                        ? "bg-gradient-to-r from-blue-600 to-indigo-600 text-white shadow-md"
                        : "text-slate-400 hover:text-slate-200 hover:bg-slate-900"
                    }`}
                  >
                    ⚡ Mode 4
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setSelectedMode("MODE_3");
                      setWsUrl(`ws://localhost:8080/ws/v1/voice?token=test_token&mode=MODE_3&formId=11111111-1111-1111-1111-111111111111`);
                    }}
                    className={`py-2 px-2 text-[11px] font-bold rounded-lg transition ${
                      selectedMode === "MODE_3"
                        ? "bg-gradient-to-r from-indigo-600 to-purple-600 text-white shadow-md"
                        : "text-slate-400 hover:text-slate-200 hover:bg-slate-900"
                    }`}
                  >
                    🎙️ Mode 3
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      const newMode = "MODE_2" as any;
                      setSelectedMode(newMode);
                      setWsUrl(`ws://localhost:8080/ws/v1/voice?token=test_token&mode=MODE_2&formId=11111111-1111-1111-1111-111111111111`);
                    }}
                    className={`py-2 px-2 text-[11px] font-bold rounded-lg transition ${
                      (selectedMode as string) === "MODE_2"
                        ? "bg-gradient-to-r from-purple-600 to-pink-600 text-white shadow-md"
                        : "text-slate-400 hover:text-slate-200 hover:bg-slate-900"
                    }`}
                  >
                    💬 Mode 2
                  </button>
                </div>
                <div className="text-[10px] text-slate-400 bg-slate-950/60 p-2 rounded-lg border border-slate-800/80 mb-3">
                  {selectedMode === "MODE_4" && "⚡ Mode 4: Native Live Voice Stream (Gemini 3.1 Flash Live ~300ms)"}
                  {selectedMode === "MODE_3" && "🎙️ Mode 3: Cascaded Multi-Vendor Pipeline (Deepgram STT → Gemini REST → Cartesia TTS ~700ms)"}
                  {(selectedMode as string) === "MODE_2" && "💬 Mode 2: Text Chat Copilot (Gemini 3.6 Flash Stateless REST)"}
                </div>
              </div>

              <div>
                <label className="text-[11px] text-slate-400 block mb-1">PostgreSQL DB Form Profile</label>
                <select
                  onChange={(e) => {
                    const selectedFormId = e.target.value;
                    setWsUrl(`ws://localhost:8080/ws/v1/voice?token=test_token&mode=${selectedMode}&formId=${selectedFormId}`);
                  }}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-blue-500 transition mb-3"
                >
                  <option value="11111111-1111-1111-1111-111111111111">
                    Form Filler: Cheerful Java Recruiter (Voice: Kore)
                  </option>
                  <option value="22222222-2222-2222-2222-222222222222">
                    Form Builder: Form Architect Co-Pilot (Voice: Puck)
                  </option>
                </select>
              </div>

              <div>
                <label className="text-[11px] text-slate-400 block mb-1">WebSocket URL</label>
                <input
                  type="text"
                  value={wsUrl}
                  onChange={(e) => setWsUrl(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-blue-500 transition"
                  placeholder="ws://localhost:8080/ws/v1/voice?token=test_token"
                />
              </div>

              {status === "DISCONNECTED" || status === "ERROR" ? (
                <button
                  onClick={connectWebSocket}
                  className="w-full py-2.5 bg-blue-600 hover:bg-blue-500 text-white rounded-lg text-xs font-semibold transition shadow-lg shadow-blue-600/20 active:scale-[0.98]"
                >
                  Connect Mode 4 Session
                </button>
              ) : (
                <button
                  onClick={disconnectWebSocket}
                  className="w-full py-2.5 bg-rose-600/80 hover:bg-rose-500 text-white rounded-lg text-xs font-semibold transition shadow-lg shadow-rose-600/20 active:scale-[0.98]"
                >
                  Disconnect Session
                </button>
              )}
            </div>
          </div>

          <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-5 backdrop-blur-md shadow-xl flex flex-col items-center text-center">
            <h2 className="text-xs font-bold text-slate-300 uppercase tracking-wider mb-4">
              2. Live Voice Stream
            </h2>

            <button
              onClick={toggleMicrophone}
              disabled={status !== "CONNECTED"}
              className={`relative w-24 h-24 rounded-full flex items-center justify-center transition-all duration-300 shadow-2xl ${
                isMicActive
                  ? isAiSpeaking
                    ? "bg-amber-600 shadow-amber-600/50 scale-100 opacity-90"
                    : "bg-rose-600 shadow-rose-600/50 scale-105 animate-pulse"
                  : status === "CONNECTED"
                  ? "bg-indigo-600 hover:bg-indigo-500 shadow-indigo-600/30 hover:scale-105"
                  : "bg-slate-800 opacity-50 cursor-not-allowed"
              }`}
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                className="w-10 h-10 text-white"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                strokeWidth={2}
              >
                {isMicActive ? (
                  <path strokeLinecap="round" strokeLinejoin="round" d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z M9 10a1 1 0 011-1h4a1 1 0 011 1v4a1 1 0 01-1 1h-4a1 1 0 01-1-1v-4z" />
                ) : (
                  <path strokeLinecap="round" strokeLinejoin="round" d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z" />
                )}
              </svg>
            </button>

            <span className="text-xs font-medium text-slate-400 mt-4">
              {isMicActive
                ? isAiSpeaking
                  ? "🔊 AI Speaking (Mic paused)..."
                  : "🎤 Streaming 16kHz PCM Mic Audio..."
                : "Click Mic button to talk"}
            </span>

            <form onSubmit={handleSendText} className="w-full mt-6 flex gap-2">
              <input
                type="text"
                value={textInput}
                onChange={(e) => setTextInput(e.target.value)}
                disabled={status !== "CONNECTED"}
                placeholder="Or type text to Gemini..."
                className="flex-1 bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-indigo-500 transition disabled:opacity-50"
              />
              <button
                type="submit"
                disabled={status !== "CONNECTED" || !textInput.trim()}
                className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white text-xs font-semibold rounded-lg transition"
              >
                Send
              </button>
            </form>
          </div>
        </div>

        {/* RIGHT PANEL: STREAMING TRANSCRIPTS */}
        <div className="lg:col-span-2 flex flex-col gap-6">
          <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-5 backdrop-blur-md shadow-xl flex flex-col h-[560px]">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3 mb-4">
              <div className="flex gap-4">
                <button
                  onClick={() => setActiveTab("transcripts")}
                  className={`text-xs font-bold uppercase tracking-wider transition ${
                    activeTab === "transcripts" ? "text-indigo-400 border-b-2 border-indigo-500 pb-1" : "text-slate-400 hover:text-slate-200"
                  }`}
                >
                  Live Transcripts ({transcripts.length})
                </button>
                <button
                  onClick={() => setActiveTab("logs")}
                  className={`text-xs font-bold uppercase tracking-wider transition ${
                    activeTab === "logs" ? "text-indigo-400 border-b-2 border-indigo-500 pb-1" : "text-slate-400 hover:text-slate-200"
                  }`}
                >
                  System Logs ({logs.length})
                </button>
              </div>

              <div className="flex gap-2">
                <button
                  onClick={stopAllAudioPlayback}
                  className="px-2.5 py-1 bg-slate-800 hover:bg-slate-700 text-slate-300 text-[11px] rounded transition"
                >
                  Stop Sound
                </button>
                <button
                  onClick={() => (activeTab === "transcripts" ? setTranscripts([]) : setLogs([]))}
                  className="px-2.5 py-1 bg-slate-800 hover:bg-slate-700 text-slate-300 text-[11px] rounded transition"
                >
                  Clear Feed
                </button>
              </div>
            </div>

            {activeTab === "transcripts" ? (
              <div
                ref={transcriptContainerRef}
                className="flex-1 overflow-y-auto space-y-4 pr-2 scrollbar-thin scrollbar-thumb-slate-800"
              >
                {transcripts.length === 0 ? (
                  <div className="h-full flex flex-col items-center justify-center text-slate-600 text-xs italic gap-2">
                    <span>No transcripts recorded yet.</span>
                    <span>Connect to Mode 4 and start speaking or typing!</span>
                  </div>
                ) : (
                  transcripts.map((t) => (
                    <div
                      key={t.id}
                      className={`p-4 rounded-xl border transition shadow-lg ${
                        t.sender === "USER"
                          ? "bg-gradient-to-r from-blue-950/50 to-indigo-950/30 border-blue-800/40 text-blue-100 ml-8"
                          : t.sender === "AI"
                          ? "bg-gradient-to-r from-purple-950/50 to-slate-900/40 border-purple-800/40 text-purple-100 mr-8"
                          : t.sender === "TOOL"
                          ? "bg-amber-950/30 border-amber-800/40 text-amber-300"
                          : "bg-slate-950/60 border-slate-800 text-slate-400"
                      }`}
                    >
                      <div className="flex items-center justify-between mb-2 opacity-70 font-mono text-[10px]">
                        <div className="flex items-center gap-2">
                          <span
                            className={`px-2 py-0.5 rounded font-bold uppercase tracking-wider text-[9px] ${
                              t.sender === "USER"
                                ? "bg-blue-500/20 text-blue-300 border border-blue-500/30"
                                : t.sender === "AI"
                                ? "bg-purple-500/20 text-purple-300 border border-purple-500/30"
                                : "bg-slate-800 text-slate-400"
                            }`}
                          >
                            {t.sender === "USER" ? "YOU" : t.sender}
                          </span>
                        </div>
                        <span>{t.timestamp}</span>
                      </div>
                      
                      <div className="text-sm font-normal leading-relaxed tracking-wide inline-block">
                        {t.text}
                        {t.sender === "AI" && isAiSpeaking && (
                          <span className="inline-block w-2 h-4 ml-1 bg-purple-400 animate-pulse align-middle rounded-sm" />
                        )}
                      </div>
                    </div>
                  ))
                )}
              </div>
            ) : (
              <div className="flex-1 overflow-y-auto font-mono text-[11px] text-slate-400 space-y-1 pr-2 scrollbar-thin scrollbar-thumb-slate-800">
                {logs.length === 0 ? (
                  <div className="h-full flex flex-col items-center justify-center text-slate-600 text-xs italic">
                    No system logs recorded.
                  </div>
                ) : (
                  logs.map((log, idx) => (
                    <div key={idx} className="hover:text-slate-200 transition">
                      {log}
                    </div>
                  ))
                )}
              </div>
            )}
          </div>
        </div>
      </main>
    </div>
  );
}
