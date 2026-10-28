package com.quantumcalc.history;

import com.quantumcalc.common.event.CalculationCompletedEvent;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/history")
public class HistoryController {

    private final HistoryStore store;

    public HistoryController(HistoryStore store) {
        this.store = store;
    }

    /** Kullanıcının son 100 işlemi (Redis'ten, anında). */
    @GetMapping("/{userId}")
    public List<CalculationCompletedEvent> history(@PathVariable String userId) {
        return store.list(userId);
    }

    /** Tüm işlemlerin servis/işlem bazında toplam sayısı. */
    @GetMapping("/analytics/summary")
    public Map<String, Long> summary() {
        return store.operationCounts();
    }
}
