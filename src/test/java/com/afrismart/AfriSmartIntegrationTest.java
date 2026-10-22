package com.afrismart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.afrismart.ai.ScriptedModelEngine;
import com.afrismart.ai.engine.ChatMessage;
import com.afrismart.ai.engine.ToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "afrismart.ai.openai.api-key=")
@AutoConfigureMockMvc
class AfriSmartIntegrationTest {

    /** Near Yaba: Yaba Campus Mart ~1 km, Surulere ~3 km (no pens), Ikeja Computer Village ~10 km. */
    private static final double LAT = 6.5158;
    private static final double LNG = 3.3791;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ScriptedModelEngine engine;

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

    @Test
    void publicSearchReturnsNearestStockedStoreFirst() throws Exception {
        mvc.perform(get("/api/v1/search").param("q", "pack of pens")
                        .param("lat", String.valueOf(LAT)).param("lng", String.valueOf(LNG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers[0].storeName").value("Yaba Campus Mart"))
                .andExpect(jsonPath("$.offers[*].storeName", Matchers.not(Matchers.hasItem("Surulere Stationers"))));
    }

    @Test
    void storeDetailsShowsPreferredStoreIsOutOfPens() throws Exception {
        String token = login("ada@afrismart.ng");
        invoke(token, "GET_STORE_DETAILS", Map.of("storeName", "Surulere", "query", "pens"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.success").value(true))
                .andExpect(jsonPath("$.result.message", Matchers.containsString("FIND_ALTERNATIVE_STORES")))
                .andExpect(jsonPath("$.result.data.stock[0].quantity").value(0));
    }

    @Test
    void findAlternativeStoresReroutesAwayFromSurulere() throws Exception {
        String token = login("ada@afrismart.ng");
        invoke(token, "FIND_ALTERNATIVE_STORES", Map.of("query", "pens", "quantity", 2, "excludeStoreName", "Surulere"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.success").value(true))
                .andExpect(jsonPath("$.result.data.recommended.storeName").value("Yaba Campus Mart"))
                .andExpect(jsonPath("$.result.data.excludedStores[0]").value("Surulere Stationers"));
    }

    @Test
    void comparePricesPicksCheapestAndNearest() throws Exception {
        String token = login("ada@afrismart.ng");
        invoke(token, "COMPARE_PRICES", Map.of("query", "pens", "quantity", 2, "radiusMeters", 20_000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.data.products[0].cheapest.storeName").value("Yaba Campus Mart"))
                .andExpect(jsonPath("$.result.data.products[0].nearest.storeName").value("Yaba Campus Mart"))
                .andExpect(jsonPath("$.result.data.products[0].storeCount").value(2));
    }

    @Test
    void toolsAreFilteredByRole() throws Exception {
        String merchant = login("yaba@afrismart.ng");
        invoke(merchant, "PLACE_ORDER", Map.of())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TOOL_NOT_AVAILABLE"));

        mvc.perform(get("/api/v1/ai/tools").header("Authorization", "Bearer " + merchant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", Matchers.hasItem("GET_LOW_STOCK_ITEMS")))
                .andExpect(jsonPath("$[*].name", Matchers.not(Matchers.hasItem("PLACE_ORDER"))));
    }

    @Test
    void locationIsRequiredForSearchTools() throws Exception {
        String token = login("ada@afrismart.ng");
        mvc.perform(post("/api/v1/ai/tools/SEARCH_PRODUCTS_NEARBY/invoke")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("arguments", Map.of("query", "pens")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.success").value(false))
                .andExpect(jsonPath("$.result.errorCode").value("LOCATION_REQUIRED"));
    }

    @Test
    void chatRunsToolLoopAndPersistsConversation() throws Exception {
        String token = login("ada@afrismart.ng");
        engine.enqueue(
                ChatMessage.assistantToolCalls(null, List.of(
                        ToolCall.function("call_1", "SEARCH_PRODUCTS_NEARBY", "{\"query\":\"pack of pens\"}")
                )),
                ChatMessage.assistant("Yaba Campus Mart has pens for ₦750.00, about 1 km away.")
        );

        String body = mvc.perform(post("/api/v1/ai/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("message", "I need a pack of pens", "latitude", LAT, "longitude", LNG))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply", Matchers.containsString("Yaba Campus Mart")))
                .andExpect(jsonPath("$.toolCalls[0].tool").value("SEARCH_PRODUCTS_NEARBY"))
                .andExpect(jsonPath("$.toolCalls[0].result.data.offers[0].storeName").value("Yaba Campus Mart"))
                .andReturn().getResponse().getContentAsString();

        List<ChatMessage> secondTurn = engine.requests().get(1);
        assertThat(secondTurn.getFirst().role()).isEqualTo("system");
        assertThat(secondTurn.getLast().role()).isEqualTo("tool");
        assertThat(secondTurn.getLast().toolCallId()).isEqualTo("call_1");
        assertThat(engine.toolRequests().getFirst()).extracting("name").doesNotContain("UPDATE_STOCK");

        String conversationId = objectMapper.readTree(body).get("conversationId").asText();
        mvc.perform(get("/api/v1/ai/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.toolCalls[0].tool").value("SEARCH_PRODUCTS_NEARBY"));

        engine.enqueue(ChatMessage.assistant("Anything else?"));
        mvc.perform(post("/api/v1/ai/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("conversationId", conversationId, "message", "thanks"))))
                .andExpect(status().isOk());
        List<ChatMessage> followUp = engine.requests().get(2);
        assertThat(followUp.get(1).content()).contains("SEARCH_PRODUCTS_NEARBY");
        assertThat(followUp).extracting(ChatMessage::content).contains("I need a pack of pens");

        String other = login("yaba@afrismart.ng");
        mvc.perform(get("/api/v1/ai/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    void orderThenMerchantSeesIncomingOrderAndSalesVelocity() throws Exception {
        String consumer = login("ada@afrismart.ng");
        JsonNode offer = objectMapper.readTree(invoke(consumer, "SEARCH_PRODUCTS_NEARBY", Map.of("query", "pens"))
                .andReturn().getResponse().getContentAsString()).at("/result/data/offers/0");

        invoke(consumer, "PLACE_ORDER", Map.of(
                "storeId", offer.get("storeId").asText(),
                "items", List.of(Map.of("productId", offer.get("productId").asText(), "quantity", 2))
        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.success").value(true))
                .andExpect(jsonPath("$.result.data.storeName").value("Yaba Campus Mart"))
                .andExpect(jsonPath("$.result.data.total").value("1500.00"))
                .andExpect(jsonPath("$.result.data.items[0].productName").isNotEmpty());

        String merchant = login("yaba@afrismart.ng");
        invoke(merchant, "GET_INCOMING_ORDERS", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.data.orders[0].status").value("PLACED"));

        String lowStock = invoke(merchant, "GET_LOW_STOCK_ITEMS", Map.of("threshold", 40))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode pens = null;
        for (JsonNode item : objectMapper.readTree(lowStock).at("/result/data/items")) {
            if (item.get("productId").asText().equals(offer.get("productId").asText())) {
                pens = item;
            }
        }
        assertThat(pens).isNotNull();
        assertThat(pens.get("unitsSoldLast7Days").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(pens.get("suggestedRestock").asLong()).isPositive();
    }

    @Test
    void aiStatusIsPublicAndChatWithoutMessageIsRejected() throws Exception {
        mvc.perform(get("/api/v1/ai/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.model").value("scripted"));

        mvc.perform(post("/api/v1/ai/chat")
                        .header("Authorization", "Bearer " + login("ada@afrismart.ng"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions invoke(String token, String tool, Map<String, Object> arguments) throws Exception {
        return mvc.perform(post("/api/v1/ai/tools/" + tool + "/invoke")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("arguments", arguments, "latitude", LAT, "longitude", LNG))));
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", DemoDataSeeder.DEMO_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
