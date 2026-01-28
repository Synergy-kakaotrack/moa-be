package com.moa.moa_backend.domain.digest.service;

import com.moa.moa_backend.domain.digest.dto.StageDigestResponse;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DigestRefreshStatusCache {

    private static final Duration TTL = Duration.ofMinutes(10);

    private record Entry(StageDigestResponse.Refresh refresh, Instant expiresAt) {}

    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();

    private String key(Long userId, Long projectId, String stage) {
        return userId + ":" + projectId + ":" + stage;
    }

    public void put(Long userId, Long projectId, String stage, StageDigestResponse.Refresh refresh) {
        map.put(key(userId, projectId, stage), new Entry(refresh, Instant.now().plus(TTL)));
    }

    public StageDigestResponse.Refresh getIfPresent(Long userId, Long projectId, String stage) {
        String k = key(userId, projectId, stage);
        Entry e = map.get(k);
        if (e == null) return null;
        if (Instant.now().isAfter(e.expiresAt)) {
            map.remove(k);
            return null;
        }
        return e.refresh;
    }

    public static OffsetDateTime nowKst() {
        return OffsetDateTime.now(ZoneOffset.ofHours(9));
    }
}
