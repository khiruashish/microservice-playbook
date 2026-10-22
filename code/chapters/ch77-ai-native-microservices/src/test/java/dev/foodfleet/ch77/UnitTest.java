package dev.foodfleet.ch77;

import dev.foodfleet.ch77.gateway.LlmGateway;
import dev.foodfleet.ch77.gateway.ResponseCache;
import dev.foodfleet.ch77.mcp.McpServer;
import dev.foodfleet.ch77.provider.StubModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class UnitTest {

    @Test
    void normalisingIsSafeAndSimilarityCanConfuseOppositeQuestions() {
        List<String> corpus = List.of("Do you have vegetarian options?", "Do you have non-vegetarian options?", "Where is my order?");
        ResponseCache exact = new ResponseCache(ResponseCache.Mode.EXACT, 1, corpus);
        ResponseCache normalised = new ResponseCache(ResponseCache.Mode.NORMALISED, 1, corpus);
        exact.put("Where is my order?", "where_is_order");
        normalised.put("Where is my order?", "where_is_order");
        assertNull(exact.get("where is my order"));
        assertEquals("where_is_order", normalised.get("  WHERE is my order").answer());
        ResponseCache similar = new ResponseCache(ResponseCache.Mode.SIMILAR, 0.6, corpus);
        similar.put("Do you have non-vegetarian options?", "nonveg_options");
        ResponseCache.Hit h = similar.get("Do you have vegetarian options?");
        assertNotNull(h, "close enough in words");
        assertEquals("nonveg_options", h.answer(), "and the wrong answer");
    }

    @Test
    void theGatewayAbandonsAStalledStreamAfterTheIdleDeadline() throws Exception {
        try (StubModel provider = new StubModel(0, new StubModel.Behaviour(8, 0, 1.0, 30), 1)) {
            LlmGateway gw = new LlmGateway(URI.create("http://127.0.0.1:" + provider.port() + "/v1/messages"),
                    new LlmGateway.Policy(Duration.ofSeconds(20), Duration.ofSeconds(5), Duration.ofMillis(300), 0), 0, Map.of());
            LlmGateway.Outcome o = gw.call("chat", "small", 50);
            assertFalse(o.ok());
            assertEquals("idle deadline", o.error());
            assertTrue(o.millis() < 5_000, o.toString());
        }
    }

    @Test
    void theMcpServerTakesIdentityFromTheTokenAndChecksHeaders() throws Exception {
        Map<String, String> tokens = Map.of("tok-asha", "asha");
        Map<String, String[]> orders = Map.of("asha", new String[] {"o-1"}, "ravi", new String[] {"o-2"});
        HttpClient http = HttpClient.newHttpClient();
        try (McpServer s = new McpServer(0, false, tokens, orders)) {
            String meta = "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\",\"io.modelcontextprotocol/clientCapabilities\":{}}";
            String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"get_order_status\",\"arguments\":{\"order_id\":\"o-2\",\"customer_id\":\"ravi\"}," + meta + "}}";
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + s.port() + "/mcp")).header("Content-Type", "application/json")
                    .header("Authorization", "Bearer tok-asha").header("MCP-Protocol-Version", "2026-07-28").POST(HttpRequest.BodyPublishers.ofString(body));
            HttpResponse<String> ok = http.send(b.copy().header("Mcp-Method", "tools/call").header("Mcp-Name", "get_order_status").build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, ok.statusCode());
            assertTrue(ok.body().contains("\"isError\":true"), "Ravi's order isn't Asha's, whatever the arguments say: " + ok.body());
            HttpResponse<String> bad = http.send(b.copy().header("Mcp-Method", "tools/call").header("Mcp-Name", "search_menu").build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, bad.statusCode());
            assertTrue(bad.body().contains("-32020"));
        }
    }
}
