package com.demo.upimesh.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IdempotencyService {

    private static final Duration RETENTION = Duration.ofHours(24);

    private final ConcurrentHashMap<String, Instant> seenPacketHashes = new ConcurrentHashMap<>();

    public boolean claim(String packetHash) {
        // Same idea as Redis SET key value NX EX 86400: only the first caller owns settlement.
        return seenPacketHashes.putIfAbsent(packetHash, Instant.now()) == null;
    }

    public void clear() {
        seenPacketHashes.clear();
    }

    public int size() {
        return seenPacketHashes.size();
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void cleanupOldHashes() {
        Instant cutoff = Instant.now().minus(RETENTION);
        for (Map.Entry<String, Instant> entry : seenPacketHashes.entrySet()) {
            if (entry.getValue().isBefore(cutoff)) {
                seenPacketHashes.remove(entry.getKey(), entry.getValue());
            }
        }
    }
}
