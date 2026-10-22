package dev.foodfleet.ch05.ordering.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.foodfleet.ch05.ordering.infrastructure.OrderEventLog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
class OrderApiTest {

    private final MockMvcTester mvc;
    private final OrderEventLog events;

    OrderApiTest(@Autowired MockMvcTester mvc, @Autowired OrderEventLog events) {
        this.mvc = mvc;
        this.events = events;
    }

    @Test
    void placeThenCancel() throws Exception {
        MvcTestResult placed = post("/api/v1/orders", order("""
                [{"menuItemId": "dum-biryani", "quantity": 2}, {"menuItemId": "boondi-raita", "quantity": 1}]"""));

        assertThat(placed).hasStatus(HttpStatus.CREATED).bodyJson().isLenientlyEqualTo("""
                {"status": "PLACED",
                 "price": {"itemTotalPaise": 76700, "packagingPaise": 3000, "gstPaise": 3985,
                           "deliveryFeePaise": 3500, "totalPaise": 87185}}
                """);
        String orderId = JsonPath.read(placed.getResponse().getContentAsString(), "$.orderId");

        assertThat(post("/api/v1/orders/" + orderId + "/cancel", """
                {"reason": "CUSTOMER_CHANGED_MIND"}"""))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");

        assertThat(events.received()).extracting(e -> e.getClass().getSimpleName())
                .contains("OrderPlaced", "OrderCancelled");
    }

    @Test
    void businessRuleViolationIs422() {
        assertThat(post("/api/v1/orders", order("""
                [{"menuItemId": "boondi-raita", "quantity": 1}]""")))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .bodyJson().extractingPath("$.detail").isEqualTo("Minimum order is ₹199.00; your items total ₹69.00");
    }

    @Test
    void invalidValueObjectIs400() {
        assertThat(post("/api/v1/orders", order("""
                [{"menuItemId": "dum-biryani", "quantity": 25}]""")))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String order(String itemsJson) {
        return """
                {"customerId": "cust-42", "restaurantId": "rest-biryani-house",
                 "address": {"line1": "12, 5th Cross", "area": "Koramangala", "city": "Bengaluru", "pincode": "560034"},
                 "items": %s}
                """.formatted(itemsJson);
    }
}
