package com.quantumcalc.common.messaging;

import com.quantumcalc.common.event.CalculationCompletedEvent;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Hesaplama servislerinin kullandığı Kafka producer.
 * Kafka erişilemese bile hesaplama sonucu kullanıcıya dönmeye devam eder (fire-and-forget).
 */
@Component
public class CalculationProducer {

    public static final String TOPIC = "calculation-events";
    private static final Logger log = LoggerFactory.getLogger(CalculationProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectProvider<Tracer> tracer;

    public CalculationProducer(KafkaTemplate<String, Object> kafkaTemplate, ObjectProvider<Tracer> tracer) {
        this.kafkaTemplate = kafkaTemplate;
        this.tracer = tracer;
    }

    public void publish(String service, String operation, String inputs, double result, String userId) {
        try {
            var event = new CalculationCompletedEvent(
                    UUID.randomUUID().toString(), service, operation, inputs, result, userId,
                    currentTraceId(), System.currentTimeMillis());
            kafkaTemplate.send(TOPIC, userId, event).whenComplete((r, ex) -> {
                if (ex != null) {
                    log.warn("Kafka'ya event gönderilemedi: {}", ex.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("Event yayınlanamadı (hesaplama etkilenmedi): {}", e.getMessage());
        }
    }

    private String currentTraceId() {
        Tracer t = tracer.getIfAvailable();
        if (t == null) return null;
        Span span = t.currentSpan();
        return span == null ? null : span.context().traceId();
    }
}
