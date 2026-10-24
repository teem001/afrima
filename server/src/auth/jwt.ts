import { SignJWT, jwtVerify } from "jose";
import { config } from "../config.js";
import type { Role } from "../types.js";

const secret = () => new TextEncoder().encode(config.jwtSecret);

export type AuthUser = { id: string; email: string; role: Role };

export async function issueToken(user: AuthUser): Promise<{ accessToken: string; expiresInSeconds: number }> {
  const expiresInSeconds = config.jwtTtlSeconds;
  const accessToken = await new SignJWT({ email: user.email, role: user.role })
    .setProtectedHeader({ alg: "HS256" })
    .setIssuer(config.jwtIssuer)
    .setSubject(user.id)
    .setIssuedAt()
    .setExpirationTime(`${expiresInSeconds}s`)
    .sign(secret());
  return { accessToken, expiresInSeconds };
}

export async function parseToken(token: string): Promise<AuthUser | null> {
  try {
    const { payload } = await jwtVerify(token, secret(), { issuer: config.jwtIssuer });
    const id = payload.sub;
    const email = payload.email as string | undefined;
    const role = payload.role as Role | undefined;
    if (!id || !email || (role !== "CONSUMER" && role !== "MERCHANT")) return null;
    return { id, email, role };
  } catch {
    return null;
  }
}
