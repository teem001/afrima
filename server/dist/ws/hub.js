const clients = new Set();
export function addClient(session) {
    clients.add(session);
}
export function removeClient(session) {
    clients.delete(session);
}
export function emit(event, data, targets) {
    const payload = JSON.stringify({ type: "event", event, data });
    for (const c of clients) {
        if (c.socket.readyState !== 1 /* OPEN */)
            continue;
        if (targets?.broadcast) {
            c.socket.send(payload);
            continue;
        }
        if (targets?.userIds && c.user && targets.userIds.includes(c.user.id)) {
            c.socket.send(payload);
        }
    }
}
//# sourceMappingURL=hub.js.map