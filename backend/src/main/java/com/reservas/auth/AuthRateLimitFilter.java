package com.reservas.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing and mass sign-ups: each client IP gets a fixed number of login and register
 * attempts per window, then 429 until the window ends. Behind nginx the IP comes from X-Forwarded-For
 * (server.forward-headers-strategy=native), which nginx overwrites so clients cannot spoof it.
 */
// shortcut: counters live in this JVM's memory, so each replica counts on its own; move them to Redis to scale out
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of("/api/auth/login", "/api/auth/register");
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private record Window(Instant start, int attempts) {
    }

    private final int maxAttempts;
    private final Duration window;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(@Value("${app.auth.rate-limit.max-attempts}") int maxAttempts,
                               @Value("${app.auth.rate-limit.window}") Duration window) {
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Instant now = Instant.now();
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.values().removeIf(w -> !now.isBefore(w.start().plus(window)));
        }
        Window current = windows.compute(request.getRemoteAddr(), (ip, w) ->
                w == null || !now.isBefore(w.start().plus(window)) ? new Window(now, 1) : new Window(w.start(), w.attempts() + 1));
        if (current.attempts() <= maxAttempts) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfter = Math.max(1, Duration.between(now, current.start().plus(window)).toSeconds());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Too many attempts. Try again in "
                + retryAfter + " seconds.\"}");
    }
}
