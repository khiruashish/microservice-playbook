package dev.foodfleet.ch22.client;

import java.net.URI;
import java.util.Map;

/**
 * An RFC 9457 problem returned by another service, as an exception the caller can switch on by
 * {@code type} (chapter 21), instead of a generic "400 Bad Request" with an unread body.
 */
public final class ApiProblemException extends RuntimeException {

    private final URI type;
    private final String title;
    private final int status;
    private final transient Map<String, Object> extensions;

    public ApiProblemException(URI type, String title, int status, String detail, Map<String, Object> extensions) {
        super(status + " " + title + (detail == null ? "" : ": " + detail));
        this.type = type;
        this.title = title;
        this.status = status;
        this.extensions = Map.copyOf(extensions);
    }

    public URI type() {
        return type;
    }

    public String title() {
        return title;
    }

    public int status() {
        return status;
    }

    public Map<String, Object> extensions() {
        return extensions;
    }

    public boolean is(String typeSuffix) {
        return type != null && type.toString().endsWith("/" + typeSuffix);
    }
}
