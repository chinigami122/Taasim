package com.taasim.gateway.security;

import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@Order(-200) // runs before JWT filter
public class CorrelationIdFilter implements WebFilter {

    public static final String HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String cid = req.getHeaders().getFirst(HEADER);
        if (cid == null || cid.isBlank()) {
            cid = UUID.randomUUID().toString();
        }
        String finalCid = cid;

        ServerHttpRequest mutated = req.mutate().header(HEADER, finalCid).build();
        exchange.getResponse().getHeaders().set(HEADER, finalCid);

        return chain.filter(exchange.mutate().request(mutated).build());
    }
}
