import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { pool } from "./pool.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/** Prefer Flyway history if present (Java deploy already migrated); else use schema_migrations. */
export async function migrate(): Promise<void> {
  const client = await pool.connect();
  try {
    const flyway = await client.query(
      `SELECT to_regclass('public.flyway_schema_history') IS NOT NULL AS exists`,
    );
    if (flyway.rows[0]?.exists) {
      const applied = await client.query(`SELECT version FROM flyway_schema_history WHERE success = true`);
      if (applied.rowCount && applied.rowCount > 0) {
        console.log(`DB already migrated via Flyway (${applied.rowCount} versions) — skipping`);
        return;
      }
    }

    await client.query(`
      CREATE TABLE IF NOT EXISTS schema_migrations (
        version TEXT PRIMARY KEY,
        applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      )
    `);

    const dir = path.join(__dirname, "migrations");
    const files = (await readdir(dir)).filter((f) => f.endsWith(".sql")).sort();
    for (const file of files) {
      const version = file.split("__")[0]!;
      const exists = await client.query(`SELECT 1 FROM schema_migrations WHERE version = $1`, [version]);
      if (exists.rowCount) continue;
      const sql = await readFile(path.join(dir, file), "utf8");
      await client.query("BEGIN");
      try {
        await client.query(sql);
        await client.query(`INSERT INTO schema_migrations (version) VALUES ($1)`, [version]);
        await client.query("COMMIT");
        console.log(`Applied migration ${file}`);
      } catch (e) {
        await client.query("ROLLBACK");
        throw e;
      }
    }
  } finally {
    client.release();
  }
}

