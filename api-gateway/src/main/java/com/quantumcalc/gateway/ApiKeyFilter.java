package com.quantumcalc.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Basit kimlik doğrulama: X-API-KEY başlığı. (WebFilter olduğu için hem route'lara hem controller'lara uygulanır.) */
@Component
public class ApiKeyFilter implements WebFilter {

    private final byte[] apiKey;

    public ApiKeyFilter(@Value("${quantumcalc.api-key}") String apiKey) {
        this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }
        String provided = exchange.getRequest().getHeaders().getFirst("X-API-KEY");
        if (provided != null && MessageDigest.isEqual(apiKey, provided.getBytes(StandardCharsets.UTF_8))) {
            return chain.filter(exchange);
        }
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
