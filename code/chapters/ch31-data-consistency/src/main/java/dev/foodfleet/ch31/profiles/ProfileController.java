package dev.foodfleet.ch31.profiles;

import dev.foodfleet.ch31.reads.ConsistencyToken;
import dev.foodfleet.ch31.reads.ReplicaRouter;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * The version travels as the ETag and comes back in If-Match (chapter 21); the consistency token travels in a
 * Consistency-Token header (a cookie works the same way for browsers). Clients send back the newest token they
 * have; responses say which server answered, for dashboards.
 */
@RestController
public class ProfileController {

    static final String TOKEN = "Consistency-Token";

    public record AddressChange(String address) {
    }

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @PutMapping("/customers/{id}/profile")
    ResponseEntity<Profile> update(@PathVariable long id, @RequestBody AddressChange change,
                                   @RequestHeader(name = "If-Match", required = false) String ifMatch) {
        if (ifMatch == null) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).build();
        }
        ProfileService.Written written = profiles.update(id, change.address(), parseVersion(ifMatch));
        return ResponseEntity.ok().eTag("\"" + written.profile().version() + "\"")
                .header(TOKEN, written.token().toString()).body(written.profile());
    }

    @GetMapping("/customers/{id}/profile")
    ResponseEntity<Profile> get(@PathVariable long id, @RequestHeader(name = TOKEN, required = false) String token) {
        Optional<ConsistencyToken> atLeast = Optional.ofNullable(token).map(ConsistencyToken::parse);
        ReplicaRouter.Routed<Optional<Profile>> read = profiles.find(id, atLeast);
        Profile p = read.value().orElseThrow(() -> new NoSuchElementException("no customer " + id));
        return ResponseEntity.ok().eTag("\"" + p.version() + "\"").header(TOKEN, read.token().toString())
                .header("Served-By", read.source().name().toLowerCase()).body(p);
    }

    static int parseVersion(String ifMatch) {
        String v = ifMatch.strip();
        if (v.startsWith("W/")) {
            v = v.substring(2);
        }
        return Integer.parseInt(v.replace("\"", ""));
    }

    @ExceptionHandler(VersionConflict.class)
    ProblemDetail conflict(VersionConflict e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.PRECONDITION_FAILED,
                "The profile was changed by someone else; reload it and apply your change again.");
        p.setProperty("currentVersion", e.current());
        return p;
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class})
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
