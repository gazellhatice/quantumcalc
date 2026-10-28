package com.quantumcalc.gateway;

import brave.Tracing;
import brave.grpc.GrpcTracing;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.grpc.ClientInterceptor;
import java.time.Duration;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    /** Giden her gRPC çağrısına trace başlıklarını ekler: Gateway -> servis aynı Trace ID'yi taşır. */
    @Bean
    @GrpcGlobalClientInterceptor
    public ClientInterceptor braveClientInterceptor(Tracing tracing) {
        return GrpcTracing.create(tracing).newClientInterceptor();
    }

    /** İleri Matematik için circuit breaker: %50 hata -> 15 sn açık; 3 sn'den uzun çağrılar zaman aşımı. */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> circuitBreakerCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(15))
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(3))
                        .build())
                .build());
    }
}
