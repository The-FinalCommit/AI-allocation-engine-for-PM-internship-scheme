package in.pragati.config;

import java.io.IOException;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Lightweight in-memory rate limiter for credential endpoints.
 * Returns 429 with a human message; no internal details.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final int limitPerMinute;
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public LoginRateLimitFilter(AppProperties props) {
        this.limitPerMinute = props.getRateLimit().getLoginPerMinute();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!"/api/auth/login".equals(request.getRequestURI()) || !"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String key = request.getRemoteAddr() + ":" + request.getRequestURI();
        long now = System.currentTimeMillis();
        Deque<Long> window = attempts.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        while (!window.isEmpty() && now - window.peekFirst() > 60_000L) {
            window.pollFirst();
        }
        if (window.size() >= limitPerMinute) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":429,\"error\":\"Too many requests\","
                    + "\"message\":\"Too many sign-in attempts. Please wait a minute and try again.\","
                    + "\"requestId\":\"" + request.getAttribute("pragati.requestId") + "\"}");
            return;
        }
        window.addLast(now);
        chain.doFilter(request, response);
    }
}
