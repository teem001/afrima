package com.afrismart.realtime;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.afrismart.shared.api.ErrorCodes;
import com.afrismart.shared.api.ErrorResponse;
import com.afrismart.shared.security.JwtService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;

@Component
class RealtimeHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeHandler.class);
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 2 * 1024 * 1024;
    private static final int TEXT_MESSAGE_LIMIT_BYTES = 64 * 1024;

    private final SessionRegistry sessions;
    private final RealtimeRouter router;
    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    /** AI chats block for seconds on the model; virtual threads keep other requests on the socket flowing. */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    RealtimeHandler(SessionRegistry sessions, RealtimeRouter router, ObjectMapper objectMapper, JwtService jwtService) {
        this.sessions = sessions;
        this.router = router;
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(TEXT_MESSAGE_LIMIT_BYTES);
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
        sessions.add(new RealtimeSession(safe, objectMapper, jwtService));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        RealtimeSession client = sessions.get(session.getId());
        if (client == null) {
            return;
        }
        RealtimeRouter.Inbound inbound;
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            inbound = new RealtimeRouter.Inbound(
                    node.path("id").asText(null),
                    node.path("type").asText(""),
                    node.get("payload")
            );
        } catch (JsonProcessingException ex) {
            client.fail(null, new ErrorResponse(
                    ErrorCodes.VALIDATION_FAILED, "Message is not valid JSON", 400, java.time.Instant.now(), java.util.List.of()
            ));
            return;
        }
        if ("ping".equals(inbound.type())) {
            client.respond(inbound.id(), "pong");
            return;
        }
        executor.submit(() -> router.handle(client, inbound));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Realtime transport error on {}: {}", session.getId(), exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
