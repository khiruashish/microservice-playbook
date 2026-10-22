package dev.foodfleet.ch36.jwt;

/** Why a token was refused. The reason is for logs and metrics; clients only ever see 401. */
public class JwtRejected extends RuntimeException {

    public enum Reason {
        MALFORMED, TOO_LARGE, ALGORITHM_NOT_ALLOWED, WRONG_TYPE, CRITICAL_HEADER, UNKNOWN_KEY, KEY_ALGORITHM_MISMATCH,
        BAD_SIGNATURE, WRONG_ISSUER, WRONG_AUDIENCE, EXPIRED, NOT_YET_VALID, ISSUED_IN_FUTURE, MISSING_CLAIM
    }

    private final Reason reason;

    public JwtRejected(Reason reason) {
        super(reason.name(), null, false, false);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
