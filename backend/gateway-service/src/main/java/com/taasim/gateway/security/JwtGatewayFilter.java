package com.taasim.gateway.security;

import com.taasim.common.security.JwtUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Order(-100)
public class JwtGatewayFilter implements WebFilter {

    // Paths that don't require a JWT
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/actuator",
            "/v3/api-docs",
            "/swagger-ui"
    );

    private final JwtUtils jwtUtils;

    public JwtGatewayFilter(@Value("${jwt.secret:dev-only-secret-please-change-me-it-must-be-256-bits-long-abcd1234}") String secret) {
        this.jwtUtils = new JwtUtils(secret);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();

        // Allow CORS preflight requests
        if (HttpMethod.OPTIONS.equals(req.getMethod())) {
            return chain.filter(exchange);
        }

        String path = req.getURI().getPath();

        // Skip public paths
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String auth = req.getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = auth.substring(7);
        if (!jwtUtils.isValid(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // Enrich downstream request with user context
        String userId = jwtUtils.getUserId(token);
        String role = jwtUtils.getRole(token);
        String email = jwtUtils.getEmail(token);

        ServerHttpRequest.Builder builder = req.mutate();
        if (userId != null) builder.header("X-User-Id", userId);
        if (role != null) builder.header("X-User-Role", role);
        if (email != null) builder.header("X-User-Email", email);

        return chain.filter(exchange.mutate().request(builder.build()).build());
    }
}
