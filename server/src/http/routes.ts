import type { FastifyInstance } from "fastify";
import { parseToken } from "../auth/jwt.js";
import { ApiError } from "../errors.js";
import * as media from "../services/mediaService.js";

export async function registerHttpRoutes(app: FastifyInstance) {
  app.post<{ Body: { contentType: string; data: string } }>("/api/v1/media", async (req, reply) => {
    const header = req.headers.authorization || "";
    const token = header.startsWith("Bearer ") ? header.slice(7) : "";
    const user = await parseToken(token);
    if (!user) throw ApiError.unauthorized("Sign in to upload media");
    const result = await media.upload(user, req.body);
    return reply.send(result);
  });

  app.get<{ Params: { mediaId: string } }>("/api/v1/media/:mediaId", async (req, reply) => {
    const asset = await media.getBytes(req.params.mediaId);
    if (!asset) throw ApiError.notFound("Media not found");
    return reply
      .header("Content-Type", asset.contentType)
      .header("Cache-Control", "public, max-age=86400")
      .send(asset.data);
  });
}
