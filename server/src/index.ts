import Fastify from "fastify";
import cors from "@fastify/cors";
import websocket from "@fastify/websocket";
import { config } from "./config.js";
import { migrate } from "./db/migrate.js";
import { getPool } from "./db/pool.js";
import { ApiError } from "./errors.js";
import { registerHttpRoutes } from "./http/routes.js";
import { addClient, removeClient, type ClientSession } from "./ws/hub.js";
import { dispatch } from "./ws/router.js";
import type { AuthUser } from "./auth/jwt.js";

async function main() {
  let ready = false;
  let bootError: string | null = null;

  const app = Fastify({ logger: true, bodyLimit: 10 * 1024 * 1024 });
  await app.register(cors, { origin: true });
  await app.register(websocket);

  // Health is registered early so Vercel probes don't kill a slow boot.
  app.get("/actuator/health", async (_req, reply) => {
    if (!ready) {
      return reply.status(503).send({ status: "STARTING", error: bootError });
    }
    return { status: "UP", db: config.memoryDb ? "memory" : "postgres" };
  });

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
          if (!ready) {
            throw ApiError.badRequest("STARTING", "Server is still starting — retry in a moment");
          }
          const msg = JSON.parse(String(raw)) as {
            id?: string;
            type?: string;
            payload?: Record<string, unknown>;
          };
          id = msg.id ?? null;
          if (!msg.type) throw ApiError.badRequest("VALIDATION_FAILED", "Missing type");
          const data = await dispatch(msg.type, msg.payload, {
            user: session.user,
            setUser,
            requestId: id,
            sendEvent: (event, data) => {
              if (socket.readyState === 1) {
                socket.send(JSON.stringify({ type: "event", event, data }));
              }
            },
          });
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
    await getPool().end().catch(() => undefined);
    process.exit(0);
  };
  process.on("SIGTERM", shutdown);
  process.on("SIGINT", shutdown);

  await app.listen({ port: config.port, host: "0.0.0.0" });
  app.log.info(`AfriSmart Node API listening on :${config.port}`);

  try {
    getPool();
    await migrate();
    ready = true;
    app.log.info(`AfriSmart ready (db=${config.memoryDb ? "memory" : "postgres"})`);
  } catch (e) {
    bootError = e instanceof Error ? e.message : String(e);
    app.log.error({ err: e }, "Database bootstrap failed");
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
