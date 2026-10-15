package com.fraudshield.gateway;

import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Verifies the JWT once, at the edge. Downstream services receive the caller's identity as
 * {@code X-User-*} headers and never parse tokens themselves. Incoming {@code X-User-*} headers
 * are always stripped first, so a client cannot spoof an identity.
 */
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    public static final String USER_ATTRIBUTE = "fraudshield.user";
    private static final List<String> PUBLIC_PATHS = List.of("/api/auth/login", "/actuator/health");

    private final JwtVerifier verifier;

    public AuthenticationFilter(JwtVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        ServerHttpRequest stripped = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Name");
                    h.remove("X-User-Role");
                })
                .build();
        if (!path.startsWith("/api/") || PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange.mutate().request(stripped).build());
        }

        Optional<Claims> claims = token(exchange).flatMap(verifier::verify);
        if (claims.isEmpty()) {
            return unauthorized(exchange);
        }
        Claims c = claims.get();
        ServerHttpRequest withIdentity = stripped.mutate()
                .header("X-User-Id", c.getSubject())
                .header("X-User-Name", String.valueOf(c.get("name")))
                .header("X-User-Role", String.valueOf(c.get("role")))
                .build();
        exchange.getAttributes().put(USER_ATTRIBUTE, c.getSubject());
        return chain.filter(exchange.mutate().request(withIdentity).build());
    }

    /** Bearer header, or {@code access_token} query parameter for EventSource (which cannot set headers). */
    private static Optional<String> token(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) return Optional.of(header.substring(7));
        return Optional.ofNullable(exchange.getRequest().getQueryParams().getFirst("access_token"));
    }

    private static Mono<Void> unauthorized(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"error\":\"Missing or invalid token\"}".getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
