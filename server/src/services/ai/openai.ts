import { config } from "../../config.js";
import { ApiError } from "../../errors.js";

export type VisionContentPart =
  | { type: "text"; text: string }
  | { type: "image_url"; image_url: { url: string; detail?: "low" | "high" | "auto" } };

export type ChatMessage = {
  role: "system" | "user" | "assistant" | "tool";
  content: string | null | VisionContentPart[];
  tool_calls?: ToolCall[];
  tool_call_id?: string;
};

export type ToolCall = {
  id: string;
  type: "function";
  function: { name: string; arguments: string };
};

export type ToolSpec = {
  type: "function";
  function: {
    name: string;
    description: string;
    parameters: Record<string, unknown>;
  };
};

export function textContent(content: ChatMessage["content"]): string {
  if (typeof content === "string") return content;
  if (!content) return "";
  return content
    .filter((p): p is { type: "text"; text: string } => p.type === "text")
    .map((p) => p.text)
    .join("\n");
}

export async function complete(
  messages: ChatMessage[],
  tools: ToolSpec[],
): Promise<ChatMessage> {
  const { apiKey, baseUrl, model } = config.openai;
  if (!apiKey) throw ApiError.badRequest("AI_UNAVAILABLE", "OPENAI_API_KEY is not set");

  const body: Record<string, unknown> = { model, messages };
  if (tools.length) {
    body.tools = tools;
    body.tool_choice = "auto";
  }

  let response: Response;
  try {
    response = await fetch(`${baseUrl}/chat/completions`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(60_000),
    });
  } catch (e) {
    throw new ApiError(502, "AI_ENGINE_ERROR", e instanceof Error ? e.message : "AI engine is unreachable");
  }

  const text = await response.text();
  let parsed: {
    choices?: { message?: ChatMessage }[];
    error?: { message?: string };
  } | null = null;
  try {
    parsed = text ? JSON.parse(text) : null;
  } catch {
    parsed = null;
  }

  if (!response.ok) {
    const detail = parsed?.error?.message || text.slice(0, 200) || response.statusText;
    throw new ApiError(502, "AI_ENGINE_ERROR", `AI engine request failed (${response.status}): ${detail}`);
  }

  const message = parsed?.choices?.[0]?.message;
  if (!message) throw new ApiError(502, "AI_ENGINE_ERROR", "AI engine returned no answer");
  return message;
}
