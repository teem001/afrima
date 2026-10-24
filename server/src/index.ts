import Fastify from "fastify";
import cors from "@fastify/cors";
import websocket from "@fastify/websocket";
import { config } from "./config.js";
import { migrate } from "./db/migrate.js";
import { pool } from "./db/pool.js";
import { ApiError } from "./errors.js";
import { registerHttpRoutes } from "./http/routes.js";
import { addClient, removeClient, type ClientSession } from "./ws/hub.js";
import { dispatch } from "./ws/router.js";
import type { AuthUser } from "./auth/jwt.js";

async function main() {
  await migrate();

  const app = Fastify({ logger: true, bodyLimit: 10 * 1024 * 1024 });
  await app.register(cors, { origin: true });
  await app.register(websocket);

  app.setErrorHandler((err, _req, reply) => {
    if (err instanceof ApiError) {
      return reply.status(err.status).send(err.toJSON());
    }
    app.log.error(err);
    return reply.status(500).send({
      code: "INTERNAL_ERROR",
      message: "Something went wrong. Please try again.",
      status: 500,
      timestamp: new Date().toISOString(),
      details: [],
    });
  });

  await registerHttpRoutes(app);

  app.register(async (instance) => {
    instance.get("/ws", { websocket: true }, (socket) => {
      const session: ClientSession = { socket, user: null };
      addClient(session);

      const setUser = (user: AuthUser | null) => {
        session.user = user;
      };

      socket.on("message", async (raw) => {
        let id: string | null = null;
        try {
          const msg = JSON.parse(String(raw)) as {
            id?: string;
            type?: string;
            payload?: Record<string, unknown>;
          };
          id = msg.id ?? null;
          if (!msg.type) throw ApiError.badRequest("VALIDATION_FAILED", "Missing type");
          const data = await dispatch(msg.type, msg.payload, { user: session.user, setUser });
          socket.send(JSON.stringify({ type: "response", id, ok: true, data }));
        } catch (e) {
          const error =
            e instanceof ApiError
              ? e.toJSON()
              : {
                  code: "INTERNAL_ERROR",
                  message: e instanceof Error ? e.message : "Something went wrong. Please try again.",
                  status: 500,
                  timestamp: new Date().toISOString(),
                  details: [],
                };
          socket.send(JSON.stringify({ type: "response", id, ok: false, error }));
        }
      });

      socket.on("close", () => removeClient(session));
    });
  });

  const shutdown = async () => {
    await app.close();
    await pool.end();
    process.exit(0);
  };
  process.on("SIGTERM", shutdown);
  process.on("SIGINT", shutdown);

  await app.listen({ port: config.port, host: "0.0.0.0" });
  app.log.info(`AfriSmart Node API on :${config.port}`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
