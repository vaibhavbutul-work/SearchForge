package com.searchforge.config.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Assigns a validated correlation ID ({@code X-Request-Id}) to every HTTP request
 * and binds it to SLF4J {@link MDC} under key {@code requestId}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";

    // Allow alphanumeric, hyphen, underscore up to 64 chars to prevent CRLF/log injection
    private static final Pattern VALID_REQUEST_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incomingId = request.getHeader(HEADER_REQUEST_ID);
        String correlationId = isValidRequestId(incomingId)
                ? incomingId.trim()
                : UUID.randomUUID().toString();

        MDC.put(MDC_REQUEST_ID, correlationId);
        response.setHeader(HEADER_REQUEST_ID, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    public static boolean isValidRequestId(String candidate) {
        return candidate != null && VALID_REQUEST_ID_PATTERN.matcher(candidate.trim()).matches();
    }
}
