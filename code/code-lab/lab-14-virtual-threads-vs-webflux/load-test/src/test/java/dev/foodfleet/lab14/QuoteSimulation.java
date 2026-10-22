package dev.foodfleet.lab14;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * An open model: requests arrive at a set rate whether or not earlier ones have finished, as real customers do.
 * A closed model (N users in a loop) slows its own arrivals when the server slows, and hides the queue that real
 * traffic builds (coordinated omission). The rates follow chapter 66: below, at and past a 2-CPU machine's capacity.
 *   mvn gatling:test -DbaseUrl=http://localhost:8094      (quote-mvc; 8096 for quote-webflux)
 */
public class QuoteSimulation extends Simulation {

    static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8094");

    static final Iterator<Map<String, Object>> RESTAURANTS = Stream.generate(
            () -> Map.<String, Object>of("restaurant", ThreadLocalRandom.current().nextInt(1, 2_001))).iterator();

    HttpProtocolBuilder protocol = http.baseUrl(BASE_URL).acceptHeader("application/json");

    ScenarioBuilder quote = scenario("quote")
            .feed(RESTAURANTS)
            .exec(http("quote").get("/quotes/r-#{restaurant}").check(status().in(200, 503)));   // a 503 is shed load

    {
        setUp(quote.injectOpen(
                constantUsersPerSec(250).during(20),
                constantUsersPerSec(1_000).during(20),
                constantUsersPerSec(1_500).during(20),
                constantUsersPerSec(2_500).during(20)))
                .protocols(protocol)
                .assertions(
                        global().responseTime().percentile(99.0).lt(500),       // the quote's latency budget
                        global().failedRequests().percent().lt(1.0));            // timeouts and errors, not 503s
    }
}
