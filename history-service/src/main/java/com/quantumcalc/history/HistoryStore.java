package com.quantumcalc.history;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantumcalc.common.event.CalculationCompletedEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis üzerinde kullanıcı başına "son N işlem" listesi tutar.
 *  - history:{userId}      -> LIST (en yeni başta, en fazla max-items eleman)
 *  - analytics:operations  -> HASH (servis:işlem -> toplam sayı)
 * Her yeni event listenin TTL'ini yeniler: kullanıcı 10 dk hareketsiz kalırsa geçmiş silinir.
 */
@Service
public class HistoryStore {

    private static final Logger log = LoggerFactory.getLogger(HistoryStore.class);
    private static final String ANALYTICS_KEY = "analytics:operations";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final int maxItems;
    private final Duration ttl;

    public HistoryStore(StringRedisTemplate redis, ObjectMapper mapper,
                        @Value("${quantumcalc.history.max-items}") int maxItems,
                        @Value("${quantumcalc.history.ttl-minutes}") long ttlMinutes) {
        this.redis = redis;
        this.mapper = mapper;
        this.maxItems = maxItems;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public void save(CalculationCompletedEvent event) throws JsonProcessingException {
        String key = key(event.userId());
        redis.opsForList().leftPush(key, mapper.writeValueAsString(event));
        redis.opsForList().trim(key, 0, maxItems - 1L);
        redis.expire(key, ttl);
        redis.opsForHash().increment(ANALYTICS_KEY, event.service() + ":" + event.operation(), 1);
    }

    public List<CalculationCompletedEvent> list(String userId) {
        List<String> raw = redis.opsForList().range(key(userId), 0, maxItems - 1L);
        List<CalculationCompletedEvent> result = new ArrayList<>();
        if (raw == null) return result;
        for (String json : raw) {
            try {
                result.add(mapper.readValue(json, CalculationCompletedEvent.class));
            } catch (JsonProcessingException e) {
                log.warn("Bozuk kayıt atlandı: {}", e.getMessage());
            }
        }
        return result;
    }

    public Map<String, Long> operationCounts() {
        Map<String, Long> counts = new TreeMap<>();
        redis.opsForHash().entries(ANALYTICS_KEY)
                .forEach((k, v) -> counts.put(String.valueOf(k), Long.parseLong(String.valueOf(v))));
        return counts;
    }

    private String key(String userId) {
        return "history:" + userId;
    }
}
