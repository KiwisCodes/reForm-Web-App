import { WebSocketServer, WebSocket } from 'ws';
import http from 'http';

const PORT = parseInt(process.env.GEMINI_MOCK_PORT || '8081', 10);

const server = http.createServer((req, res) => {
  if (req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'UP', mock: 'Gemini-WS-Mock' }));
    return;
  }
  res.writeHead(404);
  res.end();
});

const wss = new WebSocketServer({ server });

console.log(`[Gemini-WS-Mock] Starting server on port ${PORT}...`);

wss.on('connection', (ws: WebSocket, req: http.IncomingMessage) => {
  console.log(`[Gemini-WS-Mock] Client connected from ${req.socket.remoteAddress}`);

  let audioStreamInterval: NodeJS.Timeout | null = null;
  let isInterrupted = false;

  const stopAudioBroadcast = () => {
    if (audioStreamInterval) {
      clearInterval(audioStreamInterval);
      audioStreamInterval = null;
    }
  };

  ws.on('message', (message: string | Buffer) => {
    try {
      const data = JSON.parse(message.toString());

      // 1. Handshake / Setup Phase
      if (data.setup) {
        console.log('[Gemini-WS-Mock] Received setup configuration');
        ws.send(JSON.stringify({ setupComplete: {} }));
        return;
      }

      // 2. Realtime Input / Audio Frames
      if (data.realtimeInput) {
        stopAudioBroadcast();
        isInterrupted = false;

        const mediaChunks = data.realtimeInput.mediaChunks || [];
        const textChunk = mediaChunks[0]?.data;

        // Tool call emission trigger
        if (textChunk === 'TRIGGER_TOOL_CALL' || data.realtimeInput.triggerToolCall) {
          console.log('[Gemini-WS-Mock] Emitting updateFormLayout JSON tool call');
          ws.send(JSON.stringify({
            serverContent: {
              modelTurn: {
                parts: [{
                  functionCall: {
                    name: 'updateFormLayout',
                    id: `call_${Date.now()}`,
                    args: {
                      action: 'INSERT_BLOCK',
                      blockType: 'SHORT_TEXT',
                      label: 'Email Address',
                      required: true
                    }
                  }
                }]
              }
            }
          }));
          return;
        }

        // Simulating 16kHz PCM audio frame broadcasting at 20ms intervals
        let frameCount = 0;
        audioStreamInterval = setInterval(() => {
          if (ws.readyState !== WebSocket.OPEN || isInterrupted) {
            stopAudioBroadcast();
            return;
          }

          frameCount++;
          // Base64 encoded 16kHz PCM audio frame simulation (synthetic audio payload)
          const dummyPcmBase64 = 'AAAAAP////8AAAAA//';
          ws.send(JSON.stringify({
            serverContent: {
              modelTurn: {
                parts: [{
                  inlineData: {
                    mimeType: 'audio/pcm;rate=16000',
                    data: dummyPcmBase64
                  }
                }]
              }
            }
          }));

          if (frameCount >= 25) { // ~500ms audio stream completion
            stopAudioBroadcast();
            ws.send(JSON.stringify({ serverContent: { turnComplete: true } }));
          }
        }, 20);
      }

      // 3. Audio Interruption Handling
      if (data.interrupt) {
        isInterrupted = true;
        stopAudioBroadcast();
        ws.send(JSON.stringify({ serverContent: { interrupted: true } }));
      }

      // 4. Tool Response Processing
      if (data.toolResponse) {
        console.log('[Gemini-WS-Mock] Received client tool response:', data.toolResponse);
        ws.send(JSON.stringify({ serverContent: { turnComplete: true } }));
      }

    } catch (err) {
      console.error('[Gemini-WS-Mock] Error parsing message:', err);
    }
  });

  ws.on('close', () => {
    stopAudioBroadcast();
    console.log('[Gemini-WS-Mock] Client disconnected');
  });
});

server.listen(PORT, () => {
  console.log(`[Gemini-WS-Mock] Listening on ws://localhost:${PORT}`);
});
