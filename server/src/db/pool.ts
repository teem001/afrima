import pg from "pg";
import { PGlite } from "@electric-sql/pglite";
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
  /** Run a multi-statement SQL script (migrations). */
  exec: (sql: string) => Promise<void>;
};

/** Naive split on `;` outside of simple quotes — enough for our Flyway SQL files. */
function splitStatements(sql: string): string[] {
  const parts: string[] = [];
  let cur = "";
  let inSingle = false;
  for (let i = 0; i < sql.length; i++) {
    const ch = sql[i]!;
    if (ch === "'" && sql[i - 1] !== "\\") inSingle = !inSingle;
    if (ch === ";" && !inSingle) {
      const stmt = cur.trim();
      if (stmt) parts.push(stmt);
      cur = "";
      continue;
    }
    cur += ch;
  }
  const tail = cur.trim();
  if (tail) parts.push(tail);
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

function wrapPGlite(db: PGlite): AppPool {
  let chain: Promise<void> = Promise.resolve();

  const runQuery = async <T = any>(text: string, params?: unknown[]) => {
    const r = await db.query(text, params ?? []);
    const rows = (r.rows ?? []) as T[];
    const affected = (r as { affectedRows?: number }).affectedRows;
    return { rows, rowCount: affected ?? rows.length };
  };

  return {
    query: runQuery,
    async connect() {
      let releaseGate!: () => void;
      const prev = chain;
      chain = new Promise<void>((resolve) => {
        releaseGate = resolve;
      });
      await prev;
      return {
        query: runQuery,
        release: () => releaseGate(),
      };
    },
    async exec(sql: string) {
      await db.exec(sql);
    },
    async end() {
      await db.close();
    },
  };
}

async function createPool(): Promise<AppPool> {
  if (useMemoryDb()) {
    console.log("Using in-memory Postgres (PGlite) — set AFRISMART_DB_URL for a real database");
    const db = await PGlite.create();
    return wrapPGlite(db);
  }
  const pool = new pg.Pool({
    connectionString: toPgConnectionString(),
    max: config.poolSize,
    connectionTimeoutMillis: 10_000,
  });
  console.log("Using external PostgreSQL");
  return wrapPgPool(pool);
}

export const pool: AppPool = await createPool();

export async function withTx<T>(fn: (client: PoolClient) => Promise<T>): Promise<T> {
  const client = await pool.connect();
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
