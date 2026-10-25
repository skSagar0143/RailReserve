package com.railreserve.auth;

import java.io.IOException;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthRateLimitInterceptor implements HandlerInterceptor {
    private final StringRedisTemplate redis;

    public AuthRateLimitInterceptor(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI();
        int limit;
        long windowSeconds;
        if (path.endsWith("/login")) { limit = 10; windowSeconds = 60; }
        else if (path.endsWith("/register")) { limit = 5; windowSeconds = 3600; }
        else if (path.endsWith("/refresh") || path.endsWith("/logout")) { limit = 30; windowSeconds = 60; }
        else return true;

        String client = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        String key = "railreserve:auth-rate:" + path.substring(path.lastIndexOf('/') + 1) + ":" + client;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null) throw new IllegalStateException("Rate limit counter returned no value.");
            if (count == 1L) redis.expire(key, Duration.ofSeconds(windowSeconds));
            if (count > limit) {
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"rate_limit_exceeded\",\"message\":\"Too many authentication requests. Try again later.\"}");
                return false;
            }
            return true;
        } catch (Exception ex) {
            response.setStatus(503);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"rate_limiter_unavailable\",\"message\":\"Authentication is temporarily unavailable.\"}");
            return false;
        }
    }
}
