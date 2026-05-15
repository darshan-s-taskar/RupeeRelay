package com.demo.upimesh.service;

public class SettlementRejectedException extends RuntimeException {

    private final IngestionStatus status;

    public SettlementRejectedException(IngestionStatus status, String message) {
        super(message);
        this.status = status;
    }

    public IngestionStatus getStatus() {
        return status;
    }
}
