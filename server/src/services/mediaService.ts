import { v4 as uuid } from "uuid";
import type { AuthUser } from "../auth/jwt.js";
import { pool } from "../db/pool.js";
import { ApiError } from "../errors.js";

const MAX_BYTES = 8 * 1024 * 1024;

function decodeBase64(data: string): Buffer {
  const raw = data.includes(",") ? data.split(",").pop()! : data;
  return Buffer.from(raw, "base64");
}

export async function upload(user: AuthUser, input: { contentType: string; data: string }) {
  if (!input.contentType?.startsWith("image/") && !input.contentType?.startsWith("video/")) {
    throw ApiError.badRequest("VALIDATION_FAILED", "Only image or video uploads allowed");
  }
  const buf = decodeBase64(input.data || "");
  if (!buf.length || buf.length > MAX_BYTES) {
    throw ApiError.badRequest("VALIDATION_FAILED", `Media must be 1 byte … ${MAX_BYTES} bytes`);
  }
  const id = uuid();
  const now = new Date();
  await pool.query(
    `INSERT INTO media_assets (id, owner_id, content_type, byte_size, data, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$6)`,
    [id, user.id, input.contentType, buf.length, buf, now],
  );
  return { id, url: `/api/v1/media/${id}`, contentType: input.contentType, byteSize: buf.length };
}

export async function getBytes(mediaId: string): Promise<{ contentType: string; data: Buffer } | null> {
  const res = await pool.query(`SELECT content_type, data FROM media_assets WHERE id = $1`, [mediaId]);
  const row = res.rows[0];
  if (!row) return null;
  return { contentType: row.content_type, data: row.data as Buffer };
}

export function mediaUrl(id: string | null | undefined): string | null {
  return id ? `/api/v1/media/${id}` : null;
}
