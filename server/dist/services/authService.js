import bcrypt from "bcryptjs";
import { v4 as uuid } from "uuid";
import { issueToken } from "../auth/jwt.js";
import { pool } from "../db/pool.js";
import { ApiError } from "../errors.js";
function mapUser(row) {
    return {
        id: row.id,
        email: row.email,
        fullName: row.full_name,
        phone: row.phone,
        role: row.role,
    };
}
export async function register(input) {
    const email = input.email.trim().toLowerCase();
    if (!email || !input.password || input.password.length < 8) {
        throw ApiError.badRequest("VALIDATION_FAILED", "Email and password (min 8) required");
    }
    if (input.role !== "CONSUMER" && input.role !== "MERCHANT") {
        throw ApiError.badRequest("VALIDATION_FAILED", "Role must be CONSUMER or MERCHANT");
    }
    const exists = await pool.query(`SELECT 1 FROM users WHERE lower(email) = $1`, [email]);
    if (exists.rowCount)
        throw ApiError.conflict("EMAIL_TAKEN", "Email already registered");
    const id = uuid();
    const hash = await bcrypt.hash(input.password, 10);
    const now = new Date();
    await pool.query(`INSERT INTO users (id, email, password_hash, full_name, phone, role, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$7)`, [id, email, hash, input.fullName.trim(), input.phone?.trim() || null, input.role, now]);
    const user = {
        id,
        email,
        fullName: input.fullName.trim(),
        phone: input.phone?.trim() || null,
        role: input.role,
    };
    const token = await issueToken({ id, email, role: input.role });
    return { accessToken: token.accessToken, tokenType: "Bearer", expiresInSeconds: token.expiresInSeconds, user };
}
export async function login(input) {
    const email = input.email.trim().toLowerCase();
    const res = await pool.query(`SELECT id, email, password_hash, full_name, phone, role FROM users WHERE lower(email) = $1`, [email]);
    const row = res.rows[0];
    if (!row || !(await bcrypt.compare(input.password, row.password_hash))) {
        throw ApiError.unauthorized("Invalid email or password");
    }
    const user = mapUser(row);
    const token = await issueToken({ id: user.id, email: user.email, role: user.role });
    return { accessToken: token.accessToken, tokenType: "Bearer", expiresInSeconds: token.expiresInSeconds, user };
}
export async function me(user) {
    const res = await pool.query(`SELECT id, email, full_name, phone, role FROM users WHERE id = $1`, [user.id]);
    if (!res.rows[0])
        throw ApiError.unauthorized();
    return mapUser(res.rows[0]);
}
//# sourceMappingURL=authService.js.map