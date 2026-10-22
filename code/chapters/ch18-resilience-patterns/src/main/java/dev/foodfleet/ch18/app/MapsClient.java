package dev.foodfleet.ch18.app;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** The maps provider's routing API. Timeouts come from the "maps" client group's configuration. */
@HttpExchange("/v1")
public interface MapsClient {

    record Route(int durationSeconds) {
    }

    @GetExchange("/route")
    Route route(@RequestParam String from, @RequestParam String to);
}
