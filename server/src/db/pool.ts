import pg from "pg";
import { config, toPgConnectionString } from "../config.js";

const { Pool } = pg;

export const pool = new Pool({
  connectionString: toPgConnectionString(),
  max: config.poolSize,
  connectionTimeoutMillis: 10_000,
});

export type DbClient = pg.Pool | pg.PoolClient;

export async function withTx<T>(fn: (client: pg.PoolClient) => Promise<T>): Promise<T> {
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
