package com.quantumcalc.common.grpc;

import brave.Tracing;
import brave.grpc.GrpcTracing;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Gelen gRPC çağrısındaki trace başlıklarını okuyup aynı Trace ID ile devam eder. */
@Configuration
public class GrpcTracingServerConfig {

    @Bean
    @GrpcGlobalServerInterceptor
    public ServerInterceptor braveServerInterceptor(Tracing tracing) {
        return GrpcTracing.create(tracing).newServerInterceptor();
    }
}
