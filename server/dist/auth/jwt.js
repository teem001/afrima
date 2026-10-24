import { SignJWT, jwtVerify } from "jose";
import { config } from "../config.js";
const secret = () => new TextEncoder().encode(config.jwtSecret);
export async function issueToken(user) {
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
export async function parseToken(token) {
    try {
        const { payload } = await jwtVerify(token, secret(), { issuer: config.jwtIssuer });
        const id = payload.sub;
        const email = payload.email;
        const role = payload.role;
        if (!id || !email || (role !== "CONSUMER" && role !== "MERCHANT"))
            return null;
        return { id, email, role };
    }
    catch {
        return null;
    }
}
//# sourceMappingURL=jwt.js.map