package dev.foodfleet.ch35.web;

import dev.foodfleet.ch35.identity.Caller;
import dev.foodfleet.ch35.identity.Role;
import dev.foodfleet.ch35.orders.ForbiddenException;
import dev.foodfleet.ch35.outbound.WebhookClient;
import java.io.IOException;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** "Send a test notification to my webhook": a server-side request to a URL the caller chose. */
@RestController
public class WebhookController {

    public record TestRequest(String url) {
    }

    private final WebhookClient client;

    public WebhookController(WebhookClient client) {
        this.client = client;
    }

    @PostMapping("/restaurants/webhook-test")
    public Map<String, Object> test(@RequestAttribute(TokenAuthenticationFilter.CALLER) Caller caller,
                                    @RequestBody TestRequest body) throws IOException, InterruptedException {
        if (!caller.has(Role.RESTAURANT_OWNER)) {
            throw new ForbiddenException("only restaurant owners have webhooks");
        }
        WebhookClient.Result result = client.post(body.url(), "{\"type\":\"test\"}");
        // the status only: echoing the response body would turn this into a proxy for reading internal pages
        return Map.of("delivered", result.status() >= 200 && result.status() < 300, "status", result.status());
    }
}
