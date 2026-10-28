package com.quantumcalc.common.event;

/** Bir hesaplama başarıyla tamamlandığında Kafka'ya atılan olay. */
public record CalculationCompletedEvent(
        String eventId,
        String service,
        String operation,
        String inputs,
        double result,
        String userId,
        String traceId,
        long timestamp) {
}
