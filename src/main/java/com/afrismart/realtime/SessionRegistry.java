package com.afrismart.realtime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class SessionRegistry {

    private final Map<String, RealtimeSession> sessions = new ConcurrentHashMap<>();

    void add(RealtimeSession session) {
        sessions.put(session.id(), session);
    }

    void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    RealtimeSession get(String sessionId) {
        return sessions.get(sessionId);
    }

    public int size() {
        return sessions.size();
    }

    public void sendToUser(UUID userId, String event, Object data) {
        if (userId == null) {
            return;
        }
        sessions.values().stream()
                .filter(session -> session.belongsTo(userId))
                .forEach(session -> session.event(event, data));
    }

    public void broadcast(String event, Object data) {
        sessions.values().forEach(session -> session.event(event, data));
    }
}
