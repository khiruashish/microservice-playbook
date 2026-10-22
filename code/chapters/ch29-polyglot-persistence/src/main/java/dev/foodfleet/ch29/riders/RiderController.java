package dev.foodfleet.ch29.riders;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RiderController {

    public record PositionReport(double lat, double lon, float speedKmh, Instant recordedAt) {
    }

    private final RiderLocations live;
    private final PingBuffer history;

    public RiderController(RiderLocations live, PingBuffer history) {
        this.live = live;
        this.history = history;
    }

    /** The live position is written before replying; history is buffered and written within a second. */
    @PostMapping("/riders/{id}/positions")
    void report(@PathVariable long id, @RequestBody PositionReport report) {
        Ping ping = new Ping(id, report.lat(), report.lon(), report.speedKmh(),
                report.recordedAt() == null ? Instant.now() : report.recordedAt());
        live.update(ping);
        history.offer(ping);
    }

    @GetMapping("/riders/nearby")
    List<RiderLocations.NearbyRider> nearby(@RequestParam double lat, @RequestParam double lon,
                                            @RequestParam(defaultValue = "3") double radiusKm,
                                            @RequestParam(defaultValue = "10") int count) {
        return live.nearby(lat, lon, Math.min(radiusKm, 10), Math.min(Math.max(count, 1), 50));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
