package com.afrismart.realtime;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.afrismart.shared.api.ErrorResponse;
import com.afrismart.shared.security.AuthUser;
import com.afrismart.shared.security.JwtService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** A connected client. The socket must be thread-safe: responses and pushes are sent from different threads. */
public class RealtimeSession {

    private static final Logger log = LoggerFactory.getLogger(RealtimeSession.class);

    private final WebSocketSession socket;
    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private volatile String token;
    private volatile AuthUser user;

    RealtimeSession(WebSocketSession socket, ObjectMapper objectMapper, JwtService jwtService) {
        this.socket = socket;
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
    }

    public String id() {
        return socket.getId();
    }

    /** Re-checks the token so an expired session stops acting as the user without needing a reconnect. */
    public AuthUser currentUser() {
        String current = token;
        if (current == null) {
            return null;
        }
        if (jwtService.parse(current) == null) {
            unbind();
            return null;
        }
        return user;
    }

    public boolean belongsTo(UUID userId) {
        AuthUser current = currentUser();
        return current != null && current.id().equals(userId);
    }

    void bind(String token, AuthUser user) {
        this.user = user;
        this.token = token;
    }

    void unbind() {
        this.token = null;
        this.user = null;
    }

    void respond(String requestId, Object data) {
        send(new Response("response", requestId, true, data, null));
    }

    void fail(String requestId, ErrorResponse error) {
        send(new Response("response", requestId, false, null, error));
    }

    void event(String name, Object data) {
        send(new Event("event", name, data));
    }

    private void send(Object payload) {
        if (!socket.isOpen()) {
            return;
        }
        try {
            socket.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (JsonProcessingException ex) {
            log.error("Could not serialise realtime payload", ex);
        } catch (IOException | IllegalStateException ex) {
            log.debug("Realtime send to {} failed: {}", socket.getId(), ex.getMessage());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Response(String type, String id, boolean ok, Object data, ErrorResponse error) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Event(String type, String event, Object data) {
    }
}
