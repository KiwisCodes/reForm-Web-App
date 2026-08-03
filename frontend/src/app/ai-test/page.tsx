"use client";

import React, { useState } from "react";

const STATIC_TYPES = [
  "SHORT_TEXT",
  "LONG_TEXT",
  "EMAIL",
  "PHONE",
  "URL",
  "CHOICE",
  "RATING_STARS",
  "RATING_NUMBERS",
  "OPINION_SCALE",
  "DATE_TIME",
  "FILE_UPLOAD",
] as const;

interface TestChatResponse {
  schemaSent: unknown;
  requestBodySent: unknown;
  rawGeminiResponse: {
    candidates?: Array<{ content?: { parts?: Array<{ text?: string }> } }>;
  };
}

// Mode 2 prototype tester — mirrors Mode4TesterPage's role for Mode 4, but for the text-chat side.
// Talks directly to the existing throwaway AiSchemaTestController (POST /api/v1/ai/test/chat),
// no Phase 2 architecture (ChatSessionStore/FormChatPromptBuilder/AiChatService) required for this:
// that endpoint already calls real Gemini with a BlockSchemaGenerator-produced schema and returns
// the result untouched, which is all a simple input-in/output-out prototype needs.
export default function Mode2TesterPage() {
  const [staticType, setStaticType] = useState<string>("CHOICE");
  const [message, setMessage] = useState<string>(
    "Add a dropdown asking for favorite color, options Red, Blue, Green."
  );
  const [output, setOutput] = useState<string>("");
  const [rawResponse, setRawResponse] = useState<string>("");
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit() {
    setLoading(true);
    setError(null);
    setOutput("");
    setRawResponse("");

    try {
      const res = await fetch("http://localhost:8080/api/v1/ai/test/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ staticType, message }),
      });

      const bodyText = await res.text();
      if (!res.ok) {
        setError(`HTTP ${res.status}: ${bodyText}`);
        return;
      }

      const data: TestChatResponse = JSON.parse(bodyText);
      setRawResponse(JSON.stringify(data.rawGeminiResponse, null, 2));

      const text = data.rawGeminiResponse?.candidates?.[0]?.content?.parts?.[0]?.text;
      setOutput(text ?? "(could not find generated text in response — see raw response below)");
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="max-w-2xl mx-auto p-8 flex flex-col gap-4">
      <h1 className="text-xl font-semibold">Mode 2 Prototype: AI Block Generation Test</h1>
      <p className="text-sm text-gray-500">
        Prototype only — calls the throwaway test endpoint directly, no session history or
        response parsing involved yet.
      </p>

      <label className="text-sm font-medium" htmlFor="staticType">
        Block type
      </label>
      <select
        id="staticType"
        className="border rounded px-3 py-2"
        value={staticType}
        onChange={(e) => setStaticType(e.target.value)}
      >
        {STATIC_TYPES.map((type) => (
          <option key={type} value={type}>
            {type}
          </option>
        ))}
      </select>

      <label className="text-sm font-medium" htmlFor="message">
        Your message
      </label>
      <textarea
        id="message"
        className="border rounded px-3 py-2 font-mono text-sm"
        rows={3}
        value={message}
        onChange={(e) => setMessage(e.target.value)}
      />

      <button
        className="border rounded px-4 py-2 bg-black text-white disabled:opacity-50"
        onClick={handleSubmit}
        disabled={loading}
      >
        {loading ? "Sending..." : "Send to Gemini"}
      </button>

      {error && <p className="text-red-600 text-sm">{error}</p>}

      <label className="text-sm font-medium">Generated JSON (extracted output)</label>
      <pre className="border rounded px-3 py-2 bg-gray-50 text-sm whitespace-pre-wrap break-words min-h-[4rem]">
        {output || "(nothing yet)"}
      </pre>

      <details>
        <summary className="text-sm text-gray-500 cursor-pointer">Raw Gemini response (debug)</summary>
        <pre className="border rounded px-3 py-2 bg-gray-50 text-xs whitespace-pre-wrap break-words">
          {rawResponse || "(nothing yet)"}
        </pre>
      </details>
    </main>
  );
}
