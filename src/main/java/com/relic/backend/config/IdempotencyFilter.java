package com.relic.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets a client safely retry a POST that creates something (task, project,
 * approval request, assignment, registration) after a timeout or dropped
 * connection, without the retry creating a second copy of whatever the
 * first attempt already created. The client opts in by sending the same
 * "Idempotency-Key" header on the retry as on the original attempt; a
 * request without that header behaves exactly as before (keys are optional,
 * not required - plenty of legitimate POSTs are not retries of anything).
 *
 * The cache is an in-memory ConcurrentHashMap, which is correct for a
 * single instance and is the same trade-off this project's rate limiter
 * (RateLimitFilter, added alongside this) makes; a multi-instance
 * deployment would move this to a shared store (e.g. Redis) behind the
 * same filter, exactly as documented for the rate limiter.
 */
@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final long TTL_MILLIS = 24L * 60 * 60 * 1000;
    private static final Set<String> IDEMPOTENT_PATH_PREFIXES = Set.of(
            "/api/v1/tasks", "/api/v1/projects", "/api/v1/auth/register");

    private final ConcurrentHashMap<String, CachedResponse> cache = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String idempotencyKey = request.getHeader("Idempotency-Key");
        boolean applies = "POST".equalsIgnoreCase(request.getMethod())
                && idempotencyKey != null && !idempotencyKey.isBlank()
                && matchesIdempotentPath(request.getRequestURI());

        if (!applies) {
            filterChain.doFilter(request, response);
            return;
        }

        String cacheKey = request.getRequestURI() + ":" + idempotencyKey;
        CachedResponse cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            response.setStatus(cached.status());
            response.setContentType(cached.contentType());
            response.getWriter().write(cached.body());
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, wrappedResponse);

        // Only a successful creation is cached - caching a validation
        // failure or conflict would mean a client that fixed its request
        // and retried with the same key keeps getting the old error back.
        if (wrappedResponse.getStatus() >= 200 && wrappedResponse.getStatus() < 300) {
            String body = new String(wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
            cache.put(cacheKey, new CachedResponse(
                    wrappedResponse.getStatus(), wrappedResponse.getContentType(), body,
                    Instant.now().toEpochMilli() + TTL_MILLIS));
        }
        wrappedResponse.copyBodyToResponse();
    }

    private boolean matchesIdempotentPath(String uri) {
        return IDEMPOTENT_PATH_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    private record CachedResponse(int status, String contentType, String body, long expiresAtEpochMillis) {
        boolean isExpired() {
            return Instant.now().toEpochMilli() > expiresAtEpochMillis;
        }
    }
}
