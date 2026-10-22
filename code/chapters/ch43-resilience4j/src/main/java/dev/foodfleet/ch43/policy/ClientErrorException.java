package dev.foodfleet.ch43.policy;

/**
 * A dependency answered "your request is wrong" (HTTP 4xx other than 408/429): retrying won't help, and it says
 * nothing about the dependency's health, so circuit breakers ignore it and retries don't repeat it.
 */
public class ClientErrorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;

    public ClientErrorException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
