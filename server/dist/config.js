function required(name, fallback) {
    const v = process.env[name] ?? fallback;
    if (v === undefined || v === "") {
        throw new Error(`Missing required env ${name}`);
    }
    return v;
}
const rawDbUrl = (process.env.AFRISMART_DB_URL || process.env.DATABASE_URL || "").trim();
/** True when no external Postgres is configured (or explicitly memory / h2-style). */
export function useMemoryDb() {
    if (!rawDbUrl)
        return true;
    const lower = rawDbUrl.toLowerCase();
    return (lower === "memory" ||
        lower === "mem" ||
        lower === "inmemory" ||
        lower.startsWith("jdbc:h2:mem:") ||
        lower === ":memory:");
}
/** Convert jdbc:postgresql://host/db to postgresql://user:pass@host/db for node-pg. */
export function toPgConnectionString() {
    if (useMemoryDb()) {
        throw new Error("In-memory DB is active — no Postgres connection string");
    }
    const user = process.env.AFRISMART_DB_USER || process.env.PGUSER || "";
    const password = process.env.AFRISMART_DB_PASSWORD || process.env.PGPASSWORD || "";
    let url = rawDbUrl;
    if (url.startsWith("jdbc:postgresql://")) {
        url = "postgresql://" + url.slice("jdbc:postgresql://".length);
    }
    else if (url.startsWith("postgres://")) {
        url = "postgresql://" + url.slice("postgres://".length);
    }
    try {
        const u = new URL(url);
        if (!u.username && user) {
            u.username = user;
            u.password = password;
            return u.toString();
        }
    }
    catch {
        /* fall through */
    }
    return url;
}
export const config = {
    port: Number(process.env.PORT || process.env.SERVER_PORT || 8087),
    jwtSecret: required("AFRISMART_JWT_SECRET", "local-only-change-me-afrismart-jwt-hs256-key"),
    jwtIssuer: "AfriSmart",
    jwtTtlSeconds: 24 * 60 * 60,
    demoSeed: (process.env.AFRISMART_DEMO_SEED || "false").toLowerCase() === "true",
    poolSize: Number(process.env.AFRISMART_DB_POOL_SIZE || 5),
    commissionBps: 500,
    searchDefaultRadiusM: 15_000,
    searchMaxResults: 50,
    timezone: "Africa/Lagos",
    memoryDb: useMemoryDb(),
    openai: {
        apiKey: (process.env.OPENAI_API_KEY || "").trim(),
        baseUrl: (process.env.OPENAI_BASE_URL || "https://api.openai.com/v1").replace(/\/$/, ""),
        model: process.env.OPENAI_MODEL || "gpt-4o-mini",
        maxToolRounds: Number(process.env.AFRISMART_AI_MAX_TOOL_ROUNDS || 8),
        historyWindow: Number(process.env.AFRISMART_AI_HISTORY_WINDOW || 12),
    },
};
export function aiConfigured() {
    return Boolean(config.openai.apiKey);
}
//# sourceMappingURL=config.js.map