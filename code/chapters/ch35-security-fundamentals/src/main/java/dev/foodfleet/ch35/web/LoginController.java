package dev.foodfleet.ch35.web;

import dev.foodfleet.ch35.auth.LoginService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {

    public record LoginRequest(String username, String password) {
    }

    private final LoginService logins;

    public LoginController(LoginService logins) {
        this.logins = logins;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest body, HttpServletRequest request) {
        // the client address as the edge saw it; behind a proxy, configure server.forward-headers-strategy
        var outcome = logins.login(body.username(), body.password().toCharArray(), request.getRemoteAddr());
        return switch (outcome) {
            case LoginService.Success s -> ResponseEntity.ok(Map.of("token", s.token()));
            case LoginService.Failure f -> ResponseEntity.status(401).body(Map.of("error", "invalid credentials"));
            case LoginService.Throttled t -> ResponseEntity.status(429)
                    .header("Retry-After", String.valueOf(Math.max(1, t.retryAfter().toSeconds())))
                    .body(Map.of("error", "too many attempts"));
        };
    }
}
