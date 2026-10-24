function required(name: string, fallback?: string): string {
  const v = process.env[name] ?? fallback;
  if (v === undefined || v === "") {
    throw new Error(`Missing required env ${name}`);
  }
  return v;
}

/** Convert jdbc:postgresql://host/db to postgresql://user:pass@host/db for node-pg. */
export function toPgConnectionString(): string {
  const raw = process.env.AFRISMART_DB_URL || process.env.DATABASE_URL || "";
  if (!raw) {
    throw new Error("Set AFRISMART_DB_URL (jdbc:postgresql://… or postgresql://…) or DATABASE_URL");
  }
  const user = process.env.AFRISMART_DB_USER || process.env.PGUSER || "";
  const password = process.env.AFRISMART_DB_PASSWORD || process.env.PGPASSWORD || "";

  let url = raw.trim();
  if (url.startsWith("jdbc:postgresql://")) {
    url = "postgresql://" + url.slice("jdbc:postgresql://".length);
  } else if (url.startsWith("postgres://")) {
    url = "postgresql://" + url.slice("postgres://".length);
  }

  // If URL has no userinfo and we have user/password, inject them.
  try {
    const u = new URL(url);
    if (!u.username && user) {
      u.username = user;
      u.password = password;
      return u.toString();
    }
  } catch {
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
  commissionBps: 500, // 5%
  searchDefaultRadiusM: 15_000,
  searchMaxResults: 50,
  timezone: "Africa/Lagos",
};
