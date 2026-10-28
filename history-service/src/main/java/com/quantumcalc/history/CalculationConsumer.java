package com.quantumcalc.history;

import com.quantumcalc.common.event.CalculationCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CalculationConsumer {

    private static final Logger log = LoggerFactory.getLogger(CalculationConsumer.class);
    private final HistoryStore store;

    public CalculationConsumer(HistoryStore store) {
        this.store = store;
    }

    @KafkaListener(topics = "calculation-events")
    public void onEvent(CalculationCompletedEvent event) throws Exception {
        log.info("Event alındı: {}.{} user={} traceId={}",
                event.service(), event.operation(), event.userId(), event.traceId());
        store.save(event);
    }
}
