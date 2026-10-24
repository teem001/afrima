import pg from "pg";
import { newDb } from "pg-mem";
import { config, toPgConnectionString, useMemoryDb } from "../config.js";

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type QueryResult<T = any> = {
  rows: T[];
  rowCount: number | null;
};

export type DbClient = {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  query: <T = any>(text: string, params?: unknown[]) => Promise<QueryResult<T>>;
};

export type PoolClient = DbClient & { release: () => void };

export type AppPool = DbClient & {
  connect: () => Promise<PoolClient>;
  end: () => Promise<void>;
  exec: (sql: string) => Promise<void>;
};

function splitStatements(sql: string): string[] {
  const parts: string[] = [];
  let cur = "";
  let inSingle = false;
  let inLineComment = false;
  for (let i = 0; i < sql.length; i++) {
    const ch = sql[i]!;
    const next = sql[i + 1];
    if (inLineComment) {
      cur += ch;
      if (ch === "\n") inLineComment = false;
      continue;
    }
    if (!inSingle && ch === "-" && next === "-") {
      inLineComment = true;
      cur += ch;
      continue;
    }
    if (ch === "'" && sql[i - 1] !== "\\") inSingle = !inSingle;
    if (ch === ";" && !inSingle) {
      const stmt = cur.trim();
      // Skip comment-only chunks (e.g. "-- note with a semi; more text")
      const withoutComments = stmt
        .split("\n")
        .map((line) => line.replace(/--.*$/, "").trim())
        .filter(Boolean)
        .join("\n");
      if (withoutComments) parts.push(stmt);
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
    if (withoutComments) parts.push(tail);
  }
  return parts;
}

function wrapPgPool(pool: pg.Pool): AppPool {
  return {
    async query<T = any>(text: string, params?: unknown[]) {
      const r = await pool.query(text, params);
      return { rows: r.rows as T[], rowCount: r.rowCount };
    },
    async connect() {
      const client = await pool.connect();
      return {
        async query<T = any>(text: string, params?: unknown[]) {
          const r = await client.query(text, params);
          return { rows: r.rows as T[], rowCount: r.rowCount };
        },
        release: () => client.release(),
      };
    },
    async exec(sql: string) {
      const client = await pool.connect();
      try {
        for (const stmt of splitStatements(sql)) {
          await client.query(stmt);
        }
      } finally {
        client.release();
      }
    },
    end: () => pool.end(),
  };
}

function createMemoryPool(): AppPool {
  console.log("Using in-memory Postgres (pg-mem) — set AFRISMART_DB_URL for a real database");
  const mem = newDb({ autoCreateForeignKeyIndices: true });
  const adapters = mem.adapters.createPg();
  const pool = new adapters.Pool() as unknown as pg.Pool;
  return wrapPgPool(pool);
}

function createExternalPool(): AppPool {
  const pool = new pg.Pool({
    connectionString: toPgConnectionString(),
    max: config.poolSize,
    connectionTimeoutMillis: 10_000,
  });
  console.log("Using external PostgreSQL");
  return wrapPgPool(pool);
}

let _pool: AppPool | null = null;

export function getPool(): AppPool {
  if (!_pool) {
    _pool = useMemoryDb() ? createMemoryPool() : createExternalPool();
  }
  return _pool;
}

/** @deprecated use getPool() — kept for existing imports */
export const pool: AppPool = new Proxy({} as AppPool, {
  get(_t, prop, receiver) {
    const p = getPool() as unknown as Record<string | symbol, unknown>;
    const value = Reflect.get(p, prop, receiver);
    return typeof value === "function" ? value.bind(p) : value;
  },
});

export async function withTx<T>(fn: (client: PoolClient) => Promise<T>): Promise<T> {
  const client = await getPool().connect();
  try {
    await client.query("BEGIN");
    const result = await fn(client);
    await client.query("COMMIT");
    return result;
  } catch (e) {
    await client.query("ROLLBACK");
    throw e;
  } finally {
    client.release();
  }
}
