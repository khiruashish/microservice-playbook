package dev.foodfleet.ch21.api;

import java.util.Map;

/** Raised by controllers for client errors that map to an {@link ApiProblem}. */
public final class ApiException extends RuntimeException {

    final ApiProblem problem;
    final transient Map<String, Object> extensions;

    public ApiException(ApiProblem problem, String detail, Map<String, Object> extensions) {
        super(detail);
        this.problem = problem;
        this.extensions = Map.copyOf(extensions);
    }

    public ApiException(ApiProblem problem, String detail) {
        this(problem, detail, Map.of());
    }
}
