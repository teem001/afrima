package com.afrismart.realtime;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.afrismart.ai.AiAgentService;
import com.afrismart.ai.AiChatRequest;
import com.afrismart.ai.AiController;
import com.afrismart.ai.AiProgressListener;
import com.afrismart.catalog.CatalogController;
import com.afrismart.identity.AuthController;
import com.afrismart.identity.AuthResponse;
import com.afrismart.identity.LoginRequest;
import com.afrismart.identity.RegisterRequest;
import com.afrismart.inventory.InventoryController;
import com.afrismart.inventory.UpsertInventoryRequest;
import com.afrismart.marketplace.SearchController;
import com.afrismart.orders.OrderController;
import com.afrismart.orders.PlaceOrderRequest;
import com.afrismart.shared.api.ApiException;
import com.afrismart.shared.api.ErrorCodes;
import com.afrismart.shared.api.ErrorResponse;
import com.afrismart.shared.security.AuthUser;
import com.afrismart.shared.security.JwtService;
import com.afrismart.stores.StoreController;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Maps socket message types onto the REST controllers so both transports share validation, method security and
 * error codes.
 */
@Component
class RealtimeRouter {

    private static final Logger log = LoggerFactory.getLogger(RealtimeRouter.class);

    record Inbound(String id, String type, JsonNode payload) {
    }

    @FunctionalInterface
    private interface Route {
        Object handle(RealtimeSession session, String requestId, JsonNode payload) throws Exception;
    }

    record SearchParams(
            @NotBlank @Size(max = 200) String q,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
            Integer radiusMeters
    ) {
    }

    record ResumeParams(@NotBlank String token) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AiProgress(
            String requestId,
            String phase,
            UUID conversationId,
            Integer round,
            String tool,
            Boolean success,
            String message
    ) {
    }

    private final Map<String, Route> routes = new HashMap<>();
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final JwtService jwtService;

    RealtimeRouter(
            AuthController auth,
            SearchController search,
            CatalogController catalog,
            StoreController stores,
            InventoryController inventory,
            OrderController orders,
            AiController ai,
            AiAgentService agent,
            JwtService jwtService,
            ObjectMapper objectMapper,
            Validator validator
    ) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.jwtService = jwtService;

        routes.put("auth.login", (session, id, p) -> signIn(session, auth.login(read(p, LoginRequest.class))));
        routes.put("auth.register", (session, id, p) -> signIn(session, auth.register(read(p, RegisterRequest.class))));
        routes.put("auth.resume", (session, id, p) -> {
            String token = read(p, ResumeParams.class).token();
            AuthUser user = jwtService.parse(token);
            if (user == null) {
                throw ApiException.unauthorized("UNAUTHORIZED", "Session expired. Sign in again.");
            }
            SecurityContextHolder.getContext().setAuthentication(user.toAuthentication());
            Object me = auth.me();
            session.bind(token, user);
            return me;
        });
        routes.put("auth.logout", (session, id, p) -> {
            session.unbind();
            return null;
        });
        routes.put("me", (session, id, p) -> auth.me());

        routes.put("search", (session, id, p) -> {
            SearchParams params = read(p, SearchParams.class);
            return search.search(params.q().trim(), params.lat(), params.lng(), params.radiusMeters());
        });
        routes.put("catalog.products", (session, id, p) -> catalog.list());
        routes.put("stores.list", (session, id, p) -> stores.list());
        routes.put("stores.mine", (session, id, p) -> stores.mine());
        routes.put("stores.get", (session, id, p) -> stores.get(uuid(p, "storeId")));
        routes.put("inventory.list", (session, id, p) -> inventory.list(uuid(p, "storeId")));
        routes.put("inventory.upsert", (session, id, p) ->
                inventory.upsert(uuid(p, "storeId"), read(p, UpsertInventoryRequest.class)));

        routes.put("orders.place", (session, id, p) -> orders.place(read(p, PlaceOrderRequest.class)));
        routes.put("orders.mine", (session, id, p) -> orders.mine());
        routes.put("orders.get", (session, id, p) -> orders.get(uuid(p, "orderId")));
        routes.put("orders.accept", (session, id, p) -> orders.accept(uuid(p, "orderId")));
        routes.put("orders.merchant", (session, id, p) -> orders.merchantOrders());

        routes.put("ai.status", (session, id, p) -> ai.status());
        routes.put("ai.chat", (session, id, p) -> {
            AiChatRequest request = read(p, AiChatRequest.class);
            return agent.chat(AuthUser.require(), request, progress(session, id));
        });
    }

    void handle(RealtimeSession session, Inbound inbound) {
        Route route = routes.get(inbound.type());
        if (route == null) {
            session.fail(inbound.id(), error(ErrorCodes.VALIDATION_FAILED, "Unknown message type: " + inbound.type(), 400, List.of()));
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        AuthUser user = session.currentUser();
        if (user != null) {
            context.setAuthentication(user.toAuthentication());
        }
        SecurityContextHolder.setContext(context);
        try {
            session.respond(inbound.id(), route.handle(session, inbound.id(), inbound.payload()));
        } catch (Exception ex) {
            session.fail(inbound.id(), toError(inbound.type(), ex));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private AuthResponse signIn(RealtimeSession session, AuthResponse response) {
        AuthUser user = jwtService.parse(response.accessToken());
        if (user != null) {
            session.bind(response.accessToken(), user);
        }
        return response;
    }

    private AiProgressListener progress(RealtimeSession session, String requestId) {
        return new AiProgressListener() {
            @Override
            public void conversationOpened(UUID conversationId) {
                emit(new AiProgress(requestId, "conversation", conversationId, null, null, null, null));
            }

            @Override
            public void modelTurn(int round) {
                emit(new AiProgress(requestId, "thinking", null, round, null, null, null));
            }

            @Override
            public void toolStarted(String tool) {
                emit(new AiProgress(requestId, "tool_started", null, null, tool, null, null));
            }

            @Override
            public void toolFinished(String tool, boolean success, String message) {
                emit(new AiProgress(requestId, "tool_finished", null, null, tool, success, message));
            }

            private void emit(AiProgress progress) {
                session.event("ai.progress", progress);
            }
        };
    }

    private <T> T read(JsonNode payload, Class<T> type) throws JsonProcessingException {
        JsonNode node = payload == null || payload.isNull() ? objectMapper.createObjectNode() : payload;
        T value = objectMapper.treeToValue(node, type);
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            throw new InvalidPayload(violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .toList());
        }
        return value;
    }

    private static UUID uuid(JsonNode payload, String field) {
        String raw = payload == null ? null : payload.path(field).asText(null);
        if (raw == null || raw.isBlank()) {
            throw new InvalidPayload(List.of(field + " must not be blank"));
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new InvalidPayload(List.of(field + " must be a UUID"));
        }
    }

    private ErrorResponse toError(String type, Exception ex) {
        if (ex instanceof ApiException api) {
            return error(api.getCode(), api.getMessage(), api.getStatus().value(), List.of());
        }
        if (ex instanceof InvalidPayload invalid) {
            return error(ErrorCodes.VALIDATION_FAILED, "Request validation failed", 400, invalid.details);
        }
        if (ex instanceof JsonProcessingException json) {
            return error(ErrorCodes.VALIDATION_FAILED, "Malformed request", 400, List.of(json.getOriginalMessage()));
        }
        if (ex instanceof BadCredentialsException) {
            return error(ErrorCodes.INVALID_CREDENTIALS, "Invalid email or password", 401, List.of());
        }
        if (ex instanceof AuthenticationException) {
            return error("UNAUTHORIZED", "Sign in to continue", 401, List.of());
        }
        if (ex instanceof AccessDeniedException denied) {
            return error("FORBIDDEN", denied.getMessage() == null ? "Access denied" : denied.getMessage(), 403, List.of());
        }
        log.error("Realtime request {} failed", type, ex);
        return error("INTERNAL_ERROR", "Something went wrong. Please try again.", 500, List.of());
    }

    private static ErrorResponse error(String code, String message, int status, List<String> details) {
        return new ErrorResponse(code, message, status, Instant.now(), details);
    }

    private static final class InvalidPayload extends RuntimeException {
        private final List<String> details;

        InvalidPayload(List<String> details) {
            super("Request validation failed");
            this.details = details;
        }
    }
}
