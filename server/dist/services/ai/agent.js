import { v4 as uuid } from "uuid";
import { aiConfigured, config } from "../../config.js";
import { pool } from "../../db/pool.js";
import { ApiError } from "../../errors.js";
import * as preferences from "../preferenceService.js";
import { complete } from "./openai.js";
import { systemPrompt } from "./prompts.js";
import { definitionsFor, invokeTool } from "./tools.js";
const FALLBACK = "Sorry, I couldn't finish that request. Could you rephrase it or break it into smaller steps?";
export function status() {
    const configured = aiConfigured();
    return {
        engine: configured ? "OPENAI" : "NONE",
        configured,
        model: configured ? config.openai.model : null,
    };
}
async function openConversation(user, conversationId, firstMessage) {
    if (conversationId) {
        const existing = await pool.query(`SELECT id FROM ai_conversations WHERE id = $1 AND user_id = $2`, [
            conversationId,
            user.id,
        ]);
        if (existing.rows[0])
            return conversationId;
    }
    const id = uuid();
    const now = new Date();
    const title = firstMessage.slice(0, 80) || "Chat";
    await pool.query(`INSERT INTO ai_conversations (id, user_id, title, last_message_at, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$4,$4)`, [id, user.id, title, now]);
    return id;
}
async function recentToolSummary(conversationId) {
    const res = await pool.query(`SELECT tool_name, result_json, success FROM ai_tool_invocations
     WHERE conversation_id = $1 AND success = TRUE
     ORDER BY seq DESC LIMIT 8`, [conversationId]);
    if (!res.rows.length)
        return null;
    const lines = ["Recent tool results (use these storeId/productId values for PLACE_ORDER — do not invent ids):"];
    for (const row of [...res.rows].reverse()) {
        let parsed = null;
        try {
            parsed = JSON.parse(String(row.result_json));
        }
        catch {
            parsed = null;
        }
        const offers = parsed?.data?.offers ?? parsed?.data?.alternatives ?? [];
        if (offers.length) {
            lines.push(`${row.tool_name}: ` +
                offers
                    .slice(0, 5)
                    .map((o) => `${o.productName} @ ${o.storeName} (storeId=${o.storeId}, productId=${o.productId}, ₦${o.unitPrice}, qty=${o.quantity})`)
                    .join("; "));
        }
        else if (parsed?.data?.orderId) {
            lines.push(`${row.tool_name}: orderId=${parsed.data.orderId} total=₦${parsed.data.total ?? "?"}`);
        }
        else {
            lines.push(`${row.tool_name}: ok`);
        }
    }
    return lines.join("\n");
}
async function history(conversationId, limit) {
    const res = await pool.query(`SELECT role, content FROM ai_messages WHERE conversation_id = $1 ORDER BY seq ASC`, [conversationId]);
    const rows = res.rows.filter((r) => r.role === "user" || r.role === "assistant");
    const sliced = rows.slice(-limit);
    const messages = sliced.map((r) => ({
        role: r.role,
        content: r.content,
    }));
    const toolSummary = await recentToolSummary(conversationId);
    if (toolSummary) {
        messages.push({ role: "system", content: toolSummary });
    }
    return messages;
}
async function nextSeq(conversationId) {
    const res = await pool.query(`SELECT COALESCE(MAX(seq),0) AS m FROM ai_messages WHERE conversation_id = $1`, [
        conversationId,
    ]);
    return Number(res.rows[0].m) + 1;
}
async function recordExchange(conversationId, userMessage, reply, executions) {
    const now = new Date();
    let seq = await nextSeq(conversationId);
    await pool.query(`INSERT INTO ai_messages (id, conversation_id, seq, role, content, created_at, updated_at)
     VALUES ($1,$2,$3,'user',$4,$5,$5)`, [uuid(), conversationId, seq++, userMessage, now]);
    await pool.query(`INSERT INTO ai_messages (id, conversation_id, seq, role, content, created_at, updated_at)
     VALUES ($1,$2,$3,'assistant',$4,$5,$5)`, [uuid(), conversationId, seq++, reply, now]);
    for (const ex of executions) {
        await pool.query(`INSERT INTO ai_tool_invocations (id, conversation_id, seq, tool_name, arguments_json, result_json, success, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$8)`, [
            uuid(),
            conversationId,
            seq++,
            ex.tool.slice(0, 64),
            JSON.stringify(ex.arguments).slice(0, 4000),
            JSON.stringify(ex.result).slice(0, 20000),
            ex.result.success,
            now,
        ]);
    }
    await pool.query(`UPDATE ai_conversations SET last_message_at=$2, updated_at=$2 WHERE id=$1`, [
        conversationId,
        now,
    ]);
}
export async function chat(user, input, requestId, onProgress = () => undefined) {
    if (!aiConfigured()) {
        throw ApiError.badRequest("AI_UNAVAILABLE", "AI assistant is offline until OPENAI_API_KEY is set on the server");
    }
    const userMessage = (input.message || "").trim();
    if (!userMessage)
        throw ApiError.badRequest("VALIDATION_FAILED", "message is required");
    const conversationId = await openConversation(user, input.conversationId, userMessage);
    onProgress({ requestId, phase: "conversation", conversationId });
    const shopperSnippet = user.role === "CONSUMER" ? await preferences.profilePromptSnippet(user.id).catch(() => null) : null;
    const messages = [
        {
            role: "system",
            content: systemPrompt(user, input.latitude, input.longitude, input.context, shopperSnippet),
        },
        ...(await history(conversationId, config.openai.historyWindow)),
        { role: "user", content: userMessage },
    ];
    const tools = definitionsFor(user.role);
    const executions = [];
    let reply = null;
    for (let round = 0; round < config.openai.maxToolRounds; round++) {
        onProgress({ requestId, phase: "thinking", round });
        const response = await complete(messages, tools);
        const calls = response.tool_calls ?? [];
        if (!calls.length) {
            reply = response.content;
            break;
        }
        messages.push({
            role: "assistant",
            content: response.content,
            tool_calls: calls,
        });
        for (const call of calls) {
            onProgress({ requestId, phase: "tool_started", tool: call.function.name });
            const execution = await invokeTool(call.function.name, call.function.arguments || "{}", {
                user,
                latitude: input.latitude,
                longitude: input.longitude,
                cart: input.context?.cart ?? null,
            });
            executions.push(execution);
            onProgress({
                requestId,
                phase: "tool_finished",
                tool: execution.tool,
                success: execution.result.success,
                message: execution.result.message,
            });
            const toolContent = JSON.stringify(execution.result).slice(0, 12_000);
            messages.push({
                role: "tool",
                tool_call_id: call.id,
                content: toolContent,
            });
        }
    }
    if (!reply || !reply.trim())
        reply = FALLBACK;
    await recordExchange(conversationId, userMessage, reply, executions);
    return {
        conversationId,
        reply,
        toolCalls: executions.map((e) => ({
            tool: e.tool,
            arguments: e.arguments,
            result: e.result,
        })),
    };
}
//# sourceMappingURL=agent.js.map