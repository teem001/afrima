import type { WebSocket } from "ws";
import type { AuthUser } from "../auth/jwt.js";

export interface ClientSession {
  socket: WebSocket;
  user: AuthUser | null;
}

const clients = new Set<ClientSession>();

export function addClient(session: ClientSession) {
  clients.add(session);
}

export function removeClient(session: ClientSession) {
  clients.delete(session);
}

export function emit(
  event: string,
  data: unknown,
  targets?: { userIds?: string[]; broadcast?: boolean },
) {
  const payload = JSON.stringify({ type: "event", event, data });
  for (const c of clients) {
    if (c.socket.readyState !== 1 /* OPEN */) continue;
    if (targets?.broadcast) {
      c.socket.send(payload);
      continue;
    }
    if (targets?.userIds && c.user && targets.userIds.includes(c.user.id)) {
      c.socket.send(payload);
    }
  }
}
