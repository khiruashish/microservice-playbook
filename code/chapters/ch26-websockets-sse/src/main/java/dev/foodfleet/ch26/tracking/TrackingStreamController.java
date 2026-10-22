package dev.foodfleet.ch26.tracking;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The customer's tracking screen: {@code GET /orders/{id}/tracking} as Server-Sent Events.
 * Resumes after {@code Last-Event-ID}, tells each client a randomised reconnect delay (so a fleet-wide
 * disconnect doesn't come back all at once), sends a comment every 15 s to keep proxies from closing
 * the idle connection, and refuses new streams with 503 + Retry-After when this instance is full.
 */
@RestController
public class TrackingStreamController {

    static final int MAX_STREAMS = 20_000;
    static final Duration STREAM_TIMEOUT = Duration.ofMinutes(30);

    private final TrackingHub hub;
    private final Set<SseEmitter> open = ConcurrentHashMap.newKeySet();

    public TrackingStreamController(TrackingHub hub) {
        this.hub = hub;
    }

    @GetMapping(path = "/orders/{orderId}/tracking", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> track(@PathVariable String orderId,
                                            @RequestHeader(name = "Last-Event-ID", required = false) Long lastEventId)
            throws IOException {
        if (open.size() >= MAX_STREAMS) {                       // shed load early; the client retries elsewhere
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "5").build();
        }
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        open.add(emitter);
        Runnable unsubscribe = hub.subscribe(orderId, update -> send(emitter, update));
        Runnable cleanup = () -> {
            unsubscribe.run();
            open.remove(emitter);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);                             // the client reconnects with Last-Event-ID
        emitter.onError(e -> cleanup.run());

        // Randomised reconnect delay: 1-10 s, so 20,000 clients don't all return in the same second.
        emitter.send(SseEmitter.event().reconnectTime(ThreadLocalRandom.current().nextLong(1_000, 10_000))
                .comment("tracking " + orderId));
        for (TrackingHub.Update missed : hub.since(orderId, lastEventId == null ? 0 : lastEventId)) {
            send(emitter, missed);
        }
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Accel-Buffering", "no")              // nginx: don't buffer the stream
                .body(emitter);
    }

    @Scheduled(fixedDelay = 15_000)
    void heartbeat() {
        for (SseEmitter emitter : open) {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (IOException | IllegalStateException e) {
                emitter.completeWithError(e);
            }
        }
    }

    public int openStreams() {
        return open.size();
    }

    private static void send(SseEmitter emitter, TrackingHub.Update update) {
        try {
            emitter.send(SseEmitter.event().id(Long.toString(update.id())).name("position")
                    .data(update.json()));                      // a String: written as-is, not re-encoded
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);                       // client gone: triggers cleanup
        }
    }
}
