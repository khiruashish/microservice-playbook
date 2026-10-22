package dev.foodfleet.ch17.ambassador;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Everything the platform team tunes without touching the application.
 *
 * @param listenPort       loopback port the app calls (http://localhost:15001)
 * @param upstream         the real payment provider
 * @param perTryTimeout    give up on one attempt after this long
 * @param maxAttempts      attempts for retry-safe requests (GET/HEAD, or any request with an Idempotency-Key)
 * @param backoff          base delay between attempts (doubled each time, with jitter)
 * @param tokenFile        mounted secret with the provider's API token; re-read when it changes
 * @param breakerThreshold consecutive failures that open the circuit
 * @param breakerOpenFor   how long the circuit stays open before a trial request
 */
public record AmbassadorConfig(int listenPort, URI upstream, Duration perTryTimeout, int maxAttempts,
                               Duration backoff, Path tokenFile, int breakerThreshold, Duration breakerOpenFor) {
}
