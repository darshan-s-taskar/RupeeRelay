package com.demo.upimesh.service;

import java.time.Instant;

public record IngestionResult(
        IngestionStatus status,
        String packetHash,
        String packetId,
        String detail,
        Instant processedAt
) {

    public static IngestionResult of(IngestionStatus status, String packetHash, String packetId, String detail) {
        return new IngestionResult(status, packetHash, packetId, detail, Instant.now());
    }
}
