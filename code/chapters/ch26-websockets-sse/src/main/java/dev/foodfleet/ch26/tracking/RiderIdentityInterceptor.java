package dev.foodfleet.ch26.tracking;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Authentication happens once, at the handshake: the gateway has verified the rider's token and
 * set {@code X-Rider-Id} (chapter 10). No header, no WebSocket.
 */
class RiderIdentityInterceptor implements HandshakeInterceptor {

    static final String RIDER_ID = "riderId";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                   Map<String, Object> attributes) {
        String riderId = request.getHeaders().getFirst("X-Rider-Id");
        if (riderId == null || riderId.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(RIDER_ID, riderId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                               Exception exception) {
    }
}
