package dev.foodfleet.ch42.web;

import dev.foodfleet.ch42.resilience.Deadline;
import java.io.IOException;
import java.net.SocketTimeoutException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Passes the incoming request's deadline on to every downstream call, and doesn't make calls that can't finish in
 * time. Without it each hop has its own timeout, and a deep call chain keeps working long after the user is gone.
 */
public final class DeadlinePropagation implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        Object d = attrs == null ? null : attrs.getAttribute(DeadlineFilter.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (d instanceof Deadline deadline) {
            if (deadline.expired(System.currentTimeMillis())) {
                throw new SocketTimeoutException("deadline passed before calling " + request.getURI().getHost());
            }
            request.getHeaders().set(Deadline.HEADER, deadline.header());
        }
        return execution.execute(request, body);
    }
}
