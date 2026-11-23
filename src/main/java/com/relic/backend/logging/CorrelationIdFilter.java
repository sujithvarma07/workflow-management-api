package com.relic.backend.logging;

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

/**
 * Every request gets a correlation id: the caller's own X-Correlation-Id
 * header if they sent one (so a request can be traced across services that
 * all honor the same header), otherwise a freshly generated one. It is put
 * into the SLF4J MDC under "correlationId" for the lifetime of the request,
 * which the logging pattern (see application.yml's logging.pattern.console)
 * includes in every log line, and it is also echoed back on the response so
 * the caller can correlate their own logs with ours.
 *
 * Runs first in the filter chain (highest precedence) so the id is already
 * in the MDC before Spring Security's own filters - and therefore before
 * anything they log - run.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_NAME, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // MDC is thread-local and the container thread is reused for
            // the next request, so this must be cleared here rather than
            // left for garbage collection.
            MDC.remove(MDC_KEY);
        }
    }
}
