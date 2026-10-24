import pg from "pg";
import { newDb } from "pg-mem";
import { config, toPgConnectionString, useMemoryDb } from "../config.js";
function splitStatements(sql) {
    const parts = [];
    let cur = "";
    let inSingle = false;
    let inLineComment = false;
    for (let i = 0; i < sql.length; i++) {
        const ch = sql[i];
        const next = sql[i + 1];
        if (inLineComment) {
            cur += ch;
            if (ch === "\n")
                inLineComment = false;
            continue;
        }
        if (!inSingle && ch === "-" && next === "-") {
            inLineComment = true;
            cur += ch;
            continue;
        }
        if (ch === "'" && sql[i - 1] !== "\\")
            inSingle = !inSingle;
        if (ch === ";" && !inSingle) {
            const stmt = cur.trim();
            // Skip comment-only chunks (e.g. "-- note with a semi; more text")
            const withoutComments = stmt
                .split("\n")
                .map((line) => line.replace(/--.*$/, "").trim())
                .filter(Boolean)
                .join("\n");
            if (withoutComments)
                parts.push(stmt);
            cur = "";
            continue;
        }
        cur += ch;
    }
    const tail = cur.trim();
    if (tail) {
        const withoutComments = tail
            .split("\n")
            .map((line) => line.replace(/--.*$/, "").trim())
            .filter(Boolean)
            .join("\n");
        if (withoutComments)
            parts.push(tail);
    }
    return parts;
}
function wrapPgPool(pool) {
    return {
        async query(text, params) {
            const r = await pool.query(text, params);
            return { rows: r.rows, rowCount: r.rowCount };
        },
        async connect() {
            const client = await pool.connect();
            return {
                async query(text, params) {
                    const r = await client.query(text, params);
                    return { rows: r.rows, rowCount: r.rowCount };
                },
                release: () => client.release(),
            };
        },
        async exec(sql) {
            const client = await pool.connect();
            try {
                for (const stmt of splitStatements(sql)) {
                    await client.query(stmt);
                }
            }
            finally {
                client.release();
            }
        },
        async execTolerant(sql, isIgnorable) {
            const client = await pool.connect();
            try {
                for (const stmt of splitStatements(sql)) {
                    try {
                        await client.query(stmt);
                    }
                    catch (e) {
                        const msg = e instanceof Error ? e.message : String(e);
                        if (!isIgnorable(msg))
                            throw e;
                    }
                }
            }
            finally {
                client.release();
            }
        },
        end: () => pool.end(),
    };
}
function createMemoryPool() {
    console.log("Using in-memory Postgres (pg-mem) — set AFRISMART_DB_URL for a real database");
    const mem = newDb({ autoCreateForeignKeyIndices: true });
    const adapters = mem.adapters.createPg();
    const pool = new adapters.Pool();
    return wrapPgPool(pool);
}
function createExternalPool() {
    const pool = new pg.Pool({
        connectionString: toPgConnectionString(),
        max: config.poolSize,
        connectionTimeoutMillis: 10_000,
    });
    console.log("Using external PostgreSQL");
    return wrapPgPool(pool);
}
let _pool = null;
export function getPool() {
    if (!_pool) {
        _pool = useMemoryDb() ? createMemoryPool() : createExternalPool();
    }
    return _pool;
}
/** @deprecated use getPool() — kept for existing imports */
export const pool = new Proxy({}, {
    get(_t, prop, receiver) {
        const p = getPool();
        const value = Reflect.get(p, prop, receiver);
        return typeof value === "function" ? value.bind(p) : value;
    },
});
export async function withTx(fn) {
    const client = await getPool().connect();
    try {
        await client.query("BEGIN");
        const result = await fn(client);
        await client.query("COMMIT");
        return result;
    }
    catch (e) {
        await client.query("ROLLBACK");
        throw e;
    }
    finally {
        client.release();
    }
}
//# sourceMappingURL=pool.js.map