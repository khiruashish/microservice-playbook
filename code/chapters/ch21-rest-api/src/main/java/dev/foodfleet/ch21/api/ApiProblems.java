package dev.foodfleet.ch21.api;

import dev.foodfleet.ch21.orders.OrderStore;
import dev.foodfleet.ch21.paging.Cursor;
import java.util.Map;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Every error the API returns is an RFC 9457 Problem Details document
 * ({@code application/problem+json}): type, title, status, detail, instance, plus extensions
 * clients can use (the current ETag on a 412, the allowed range on a bad page size).
 */
@RestControllerAdvice
class ApiProblems {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> api(ApiException e) {
        return problem(e.problem, e.getMessage(), e.extensions);
    }

    @ExceptionHandler(Cursor.InvalidCursor.class)
    ResponseEntity<ProblemDetail> cursor(Cursor.InvalidCursor e) {
        return problem(ApiProblem.INVALID_CURSOR, "Use the 'next' link from the previous page as is.", Map.of());
    }

    @ExceptionHandler(OrderStore.OrderNotFound.class)
    ResponseEntity<ProblemDetail> notFound(OrderStore.OrderNotFound e) {
        return problem(ApiProblem.ORDER_NOT_FOUND, e.getMessage(), Map.of());
    }

    @ExceptionHandler(OrderStore.VersionConflict.class)
    ResponseEntity<ProblemDetail> conflict(OrderStore.VersionConflict e) {
        String current = OrderHistoryController.etag(e.currentVersion());
        return ResponseEntity.status(ApiProblem.STALE_VERSION.status)
                .eTag(current)
                .body(detail(ApiProblem.STALE_VERSION,
                        "Fetch the order again, reapply your change, and send the new ETag in If-Match.",
                        Map.of("currentEtag", current)));
    }

    private static ResponseEntity<ProblemDetail> problem(ApiProblem p, String detail, Map<String, Object> extensions) {
        return ResponseEntity.status(p.status).body(detail(p, detail, extensions));
    }

    private static ProblemDetail detail(ApiProblem p, String detail, Map<String, Object> extensions) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(p.status, detail);
        problem.setType(p.type);
        problem.setTitle(p.title);
        extensions.forEach(problem::setProperty);
        return problem;
    }
}
