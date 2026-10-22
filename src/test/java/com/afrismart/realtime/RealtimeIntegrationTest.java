package com.afrismart.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.afrismart.DemoDataSeeder;
import com.afrismart.ai.ScriptedModelEngine;
import com.afrismart.ai.engine.ChatMessage;
import com.afrismart.ai.engine.ToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "afrismart.ai.openai.api-key=",
                "spring.datasource.url=jdbc:h2:mem:afrismart-realtime;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
        }
)
class RealtimeIntegrationTest {

    private static final double LAT = 6.5158;
    private static final double LNG = 3.3791;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ScriptedModelEngine engine;

    private final List<Client> clients = new ArrayList<>();

    @TestConfiguration
    static class ScriptedEngineConfig {
        @Bean
        ScriptedModelEngine scriptedModelEngine() {
            return new ScriptedModelEngine();
        }
    }

    @BeforeEach
    void resetEngine() {
        engine.reset();
    }

    @AfterEach
    void closeClients() throws Exception {
        for (Client client : clients) {
            client.socket.close();
        }
    }

    @Test
    void anonymousRequestsWorkUntilAuthIsNeeded() throws Exception {
        Client client = connect();

        assertThat(client.request("ping", null).get("data").asText()).isEqualTo("pong");

        JsonNode search = client.request("search", Map.of("q", "pack of pens", "lat", LAT, "lng", LNG));
        assertThat(search.get("ok").asBoolean()).isTrue();
        assertThat(search.at("/data/offers/0/storeName").asText()).isEqualTo("Yaba Campus Mart");

        JsonNode orders = client.request("orders.mine", null);
        assertThat(orders.get("ok").asBoolean()).isFalse();
        assertThat(orders.at("/error/status").asInt()).isEqualTo(401);

        JsonNode badLogin = client.request("auth.login", Map.of("email", "ada@afrismart.ng", "password", "wrong-password"));
        assertThat(badLogin.at("/error/code").asText()).isEqualTo("INVALID_CREDENTIALS");

        JsonNode missingQuery = client.request("search", Map.of("lat", LAT, "lng", LNG));
        assertThat(missingQuery.at("/error/code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(missingQuery.at("/error/details/0").asText()).startsWith("q ");

        assertThat(client.request("nope", null).at("/error/code").asText()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void resumeRestoresIdentityAndLogoutDropsIt() throws Exception {
        Client first = connect();
        String token = first.request("auth.login", login("ada@afrismart.ng")).at("/data/accessToken").asText();

        Client second = connect();
        JsonNode resumed = second.request("auth.resume", Map.of("token", token));
        assertThat(resumed.at("/data/email").asText()).isEqualTo("ada@afrismart.ng");
        assertThat(second.request("orders.mine", null).get("ok").asBoolean()).isTrue();

        second.request("auth.logout", null);
        assertThat(second.request("orders.mine", null).at("/error/status").asInt()).isEqualTo(401);

        assertThat(connect().request("auth.resume", Map.of("token", "garbage")).at("/error/status").asInt()).isEqualTo(401);
    }

    @Test
    void searchBarChatStreamsProgressAndSendsClientContextToTheModel() throws Exception {
        Client client = connect();
        client.request("auth.login", login("ada@afrismart.ng"));
        engine.enqueue(
                ChatMessage.assistantToolCalls(null, List.of(
                        ToolCall.function("call_1", "SEARCH_PRODUCTS_NEARBY", "{\"query\":\"pack of pens\",\"radiusMeters\":5000}")
                )),
                ChatMessage.assistant("Yaba Campus Mart has pens for ₦750.00, about 1 km away.")
        );

        JsonNode response = client.request("ai.chat", Map.of(
                "message", "pack of pens",
                "latitude", LAT,
                "longitude", LNG,
                "context", Map.of(
                        "screen", "search",
                        "searchQuery", "pack of pens",
                        "locationLabel", "Yaba",
                        "radiusMeters", 5000
                )
        ));

        assertThat(response.get("ok").asBoolean()).isTrue();
        assertThat(response.at("/data/reply").asText()).contains("Yaba Campus Mart");
        assertThat(response.at("/data/toolCalls/0/result/data/offers/0/storeName").asText()).isEqualTo("Yaba Campus Mart");

        List<String> phases = client.events.stream()
                .filter(event -> event.get("event").asText().equals("ai.progress"))
                .peek(event -> assertThat(event.at("/data/requestId").asText()).isEqualTo(response.get("id").asText()))
                .map(event -> event.at("/data/phase").asText())
                .toList();
        assertThat(phases).containsExactly("conversation", "thinking", "tool_started", "tool_finished", "thinking");

        String systemPrompt = engine.requests().getFirst().getFirst().content();
        assertThat(systemPrompt).contains("search bar").contains("5000 m").contains("Area: Yaba");
    }

    @Test
    void orderAndStockChangesArePushedToTheRightPeople() throws Exception {
        Client consumer = connect();
        consumer.request("auth.login", login("ada@afrismart.ng"));
        Client merchant = connect();
        merchant.request("auth.login", login("yaba@afrismart.ng"));
        Client bystander = connect();

        assertThat(consumer.request("wallet.fund", Map.of("amountKobo", 1_000_000)).get("ok").asBoolean()).isTrue();
        JsonNode offer = consumer.request("search", Map.of("q", "pens", "lat", LAT, "lng", LNG)).at("/data/offers/0");
        JsonNode placed = consumer.request("orders.place", Map.of(
                "storeId", offer.get("storeId").asText(),
                "items", List.of(Map.of("productId", offer.get("productId").asText(), "quantity", 1))
        ));
        assertThat(placed.get("ok").asBoolean()).isTrue();
        String orderId = placed.at("/data/id").asText();

        JsonNode incoming = merchant.awaitEvent("order.placed");
        assertThat(incoming.at("/data/id").asText()).isEqualTo(orderId);
        assertThat(bystander.awaitEvent("stock.changed").at("/data/productId").asText())
                .isEqualTo(offer.get("productId").asText());

        assertThat(consumer.request("orders.merchant", null).at("/error/status").asInt()).isEqualTo(403);

        assertThat(merchant.request("orders.accept", Map.of("orderId", orderId)).get("ok").asBoolean()).isTrue();
        assertThat(consumer.awaitEvent("order.updated").at("/data/status").asText()).isEqualTo("ACCEPTED");
        assertThat(bystander.events).noneMatch(event -> event.get("event").asText().startsWith("order."));
    }

    @Test
    void walletEscrowPayoutAnchorAndTwinsFlow() throws Exception {
        Client consumer = connect();
        consumer.request("auth.register", Map.of(
                "email", "money-twin@example.com", "password", "Password123!",
                "fullName", "Money Twin", "role", "CONSUMER"));
        Client merchant = connect();
        merchant.request("auth.login", login("yaba@afrismart.ng"));

        // Insufficient funds blocks ordering before any stock is reserved.
        JsonNode offer = consumer.request("search", Map.of("q", "pens", "lat", LAT, "lng", LNG)).at("/data/offers/0");
        JsonNode broke = consumer.request("orders.place", Map.of(
                "storeId", offer.get("storeId").asText(),
                "items", List.of(Map.of("productId", offer.get("productId").asText(), "quantity", 1))));
        assertThat(broke.at("/error/code").asText()).isEqualTo("INSUFFICIENT_FUNDS");

        assertThat(consumer.request("wallet.fund", Map.of("amountKobo", 1_000_000))
                .at("/data/balanceKobo").asLong()).isEqualTo(1_000_000);

        JsonNode baseline = merchant.request("payments.merchantSummary", null);
        long baseWallet = baseline.at("/data/walletBalanceKobo").asLong();
        long baseHeld = baseline.at("/data/inEscrowKobo").asLong();
        long baseCommission = baseline.at("/data/commissionKobo").asLong();

        JsonNode placed = consumer.request("orders.place", Map.of(
                "storeId", offer.get("storeId").asText(),
                "items", List.of(Map.of("productId", offer.get("productId").asText(), "quantity", 1))));
        assertThat(placed.get("ok").asBoolean()).isTrue();
        String orderId = placed.at("/data/id").asText();

        assertThat(consumer.request("wallet.me", null).at("/data/balanceKobo").asLong()).isEqualTo(925_000);
        JsonNode payment = consumer.request("payments.order", Map.of("orderId", orderId));
        assertThat(payment.at("/data/status").asText()).isEqualTo("IN_ESCROW");
        assertThat(payment.at("/data/amountKobo").asLong()).isEqualTo(75_000);

        assertThat(merchant.request("orders.accept", Map.of("orderId", orderId)).get("ok").asBoolean()).isTrue();
        assertThat(merchant.request("orders.fulfil", Map.of("orderId", orderId)).at("/data/status").asText())
                .isEqualTo("FULFILLED");

        // Escrow released minus 5% commission: 75,000 -> 71,250 to the merchant.
        JsonNode summary = merchant.request("payments.merchantSummary", null);
        assertThat(summary.at("/data/walletBalanceKobo").asLong()).isEqualTo(baseWallet + 71_250);
        assertThat(summary.at("/data/inEscrowKobo").asLong()).isEqualTo(baseHeld);
        assertThat(summary.at("/data/commissionKobo").asLong()).isEqualTo(baseCommission + 3_750);

        JsonNode verify = consumer.request("verify", Map.of("orderId", orderId));
        assertThat(verify.at("/data/hashMatches").asBoolean()).isTrue();
        assertThat(verify.at("/data/anchored").asBoolean()).isTrue();
        assertThat(verify.at("/data/proofValid").asBoolean()).isTrue();
        assertThat(verify.at("/data/anchorStatus").asText()).isEqualTo("SIMULATED");

        JsonNode payout = merchant.request("payouts.request", Map.of("amountKobo", 71_250));
        assertThat(payout.at("/data/status").asText()).isEqualTo("SIMULATED_PAID");
        assertThat(merchant.request("wallet.me", null).at("/data/balanceKobo").asLong()).isEqualTo(baseWallet);

        JsonNode shopTwin = merchant.request("twin.shop", Map.of("storeId", offer.get("storeId").asText()));
        assertThat(shopTwin.at("/data/storeName").asText()).isEqualTo("Yaba Campus Mart");
        assertThat(shopTwin.at("/data/stock/0/productName").asText()).isNotBlank();

        JsonNode simulation = merchant.request("twin.simulate", Map.of(
                "storeId", offer.get("storeId").asText(),
                "productId", offer.get("productId").asText(),
                "restockQty", 50,
                "priceChangePercent", -10));
        assertThat(simulation.at("/data/restockQty").asInt()).isEqualTo(50);
        assertThat(simulation.at("/data/note").asText()).contains("never changes real stock");

        JsonNode myTwin = consumer.request("twin.me", null);
        assertThat(myTwin.at("/data/wallet/balanceKobo").asLong()).isEqualTo(925_000);
        assertThat(myTwin.at("/data/ordersCount").asInt()).isGreaterThanOrEqualTo(1);
    }

    private Map<String, Object> login(String email) {
        return Map.of("email", email, "password", DemoDataSeeder.DEMO_PASSWORD);
    }

    private Client connect() throws Exception {
        Client client = new Client();
        client.socket = new StandardWebSocketClient()
                .execute(client, new WebSocketHttpHeaders(), URI.create("ws://localhost:" + port + "/ws"))
                .get(5, TimeUnit.SECONDS);
        clients.add(client);
        return client;
    }

    private class Client extends TextWebSocketHandler {
        private final BlockingQueue<JsonNode> inbox = new LinkedBlockingQueue<>();
        private final List<JsonNode> events = new ArrayList<>();
        private WebSocketSession socket;

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            inbox.add(objectMapper.readTree(message.getPayload()));
        }

        JsonNode request(String type, Object payload) throws Exception {
            String id = UUID.randomUUID().toString();
            Map<String, Object> message = new java.util.HashMap<>(Map.of("id", id, "type", type));
            if (payload != null) {
                message.put("payload", payload);
            }
            socket.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            while (true) {
                JsonNode next = take();
                if ("event".equals(next.get("type").asText())) {
                    events.add(next);
                } else if (id.equals(next.path("id").asText())) {
                    return next;
                }
            }
        }

        JsonNode awaitEvent(String name) throws Exception {
            for (JsonNode event : events) {
                if (name.equals(event.get("event").asText())) {
                    return event;
                }
            }
            while (true) {
                JsonNode next = take();
                if ("event".equals(next.get("type").asText())) {
                    events.add(next);
                    if (name.equals(next.get("event").asText())) {
                        return next;
                    }
                }
            }
        }

        private JsonNode take() throws InterruptedException {
            JsonNode next = inbox.poll(10, TimeUnit.SECONDS);
            assertThat(next).as("message from server").isNotNull();
            return next;
        }
    }
}
