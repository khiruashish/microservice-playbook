package dev.foodfleet.ch26.tracking;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Rider apps send {@code {"orderId":"o-1","lat":12.9,"lng":77.6}} over a WebSocket, several times
 * a minute for the whole shift: one long-lived connection instead of a request per position.
 * Updates faster than one per second per rider are dropped; malformed messages close the session.
 */
@Component
public class RiderLocationHandler extends TextWebSocketHandler {

    static final Pattern MESSAGE = Pattern.compile(
            "\\{\\s*\"orderId\"\\s*:\\s*\"([\\w-]{1,40})\"\\s*,\\s*\"lat\"\\s*:\\s*(-?\\d{1,2}\\.\\d+)\\s*,\\s*\"lng\"\\s*:\\s*(-?\\d{1,3}\\.\\d+)\\s*}");
    static final long MIN_INTERVAL_MS = 1_000;

    private final TrackingHub hub;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAccepted = new ConcurrentHashMap<>();

    public RiderLocationHandler(TrackingHub hub) {
        this.hub = hub;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Sends from other threads must not interleave, and a slow phone must not block us:
        // at most 5 s per send and 64 KB buffered, then the session is closed.
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, 5_000, 64 * 1024));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        Matcher m = MESSAGE.matcher(message.getPayload());
        if (!m.matches()) {
            session.close(CloseStatus.BAD_DATA.withReason("expected {orderId, lat, lng}"));
            return;
        }
        String riderId = (String) session.getAttributes().get(RiderIdentityInterceptor.RIDER_ID);
        long now = System.currentTimeMillis();
        Long previous = lastAccepted.get(session.getId());
        if (previous != null && now - previous < MIN_INTERVAL_MS) {
            return;                                             // too frequent: drop, the next one will do
        }
        lastAccepted.put(session.getId(), now);
        TrackingHub.Update update = hub.publish(m.group(1),
                "{\"orderId\":\"%s\",\"riderId\":\"%s\",\"lat\":%s,\"lng\":%s,\"at\":%d}"
                        .formatted(m.group(1), riderId, m.group(2), m.group(3), now));
        sessions.get(session.getId()).sendMessage(new TextMessage("{\"ack\":" + update.id() + "}"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        lastAccepted.remove(session.getId());
    }
}
