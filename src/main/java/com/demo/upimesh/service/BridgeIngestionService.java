package com.demo.upimesh.service;

import com.demo.upimesh.crypto.CryptoException;
import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Service
public class BridgeIngestionService {

    private static final Duration MAX_PAYMENT_AGE = Duration.ofHours(24);
    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private final HybridCryptoService cryptoService;
    private final IdempotencyService idempotencyService;
    private final AccountRepository accountRepository;
    private final SettlementService settlementService;

    public BridgeIngestionService(
            HybridCryptoService cryptoService,
            IdempotencyService idempotencyService,
            AccountRepository accountRepository,
            SettlementService settlementService
    ) {
        this.cryptoService = cryptoService;
        this.idempotencyService = idempotencyService;
        this.accountRepository = accountRepository;
        this.settlementService = settlementService;
    }

    public IngestionResult ingest(MeshPacket packet) {
        if (packet == null || packet.getCiphertext() == null || packet.getCiphertext().isBlank()) {
            return IngestionResult.of(IngestionStatus.INVALID_PAYMENT_REJECTED, null, null, "Missing ciphertext");
        }

        String packetHash = cryptoService.computeCiphertextHash(packet.getCiphertext());
        if (!idempotencyService.claim(packetHash)) {
            return IngestionResult.of(IngestionStatus.DUPLICATE_DROPPED, packetHash, packet.getPacketId(), "Packet hash was already claimed");
        }

        PaymentInstruction instruction;
        try {
            instruction = cryptoService.decrypt(packet.getCiphertext());
        } catch (CryptoException ex) {
            return IngestionResult.of(IngestionStatus.TAMPERED_REJECTED, packetHash, packet.getPacketId(), "Ciphertext authentication failed");
        }

        IngestionResult freshnessResult = validateFreshness(packetHash, packet, instruction);
        if (freshnessResult != null) {
            return freshnessResult;
        }

        Account sender = accountRepository.findByUpiId(instruction.getSenderUpiId()).orElse(null);
        if (sender == null) {
            return IngestionResult.of(IngestionStatus.UNKNOWN_ACCOUNT_REJECTED, packetHash, packet.getPacketId(), "Sender account not found");
        }
        if (!Objects.equals(sender.getPinHash(), instruction.getPinHash())) {
            return IngestionResult.of(IngestionStatus.INVALID_PIN_REJECTED, packetHash, packet.getPacketId(), "Sender PIN hash did not match");
        }

        try {
            settlementService.settle(instruction, packetHash);
            return IngestionResult.of(IngestionStatus.SETTLED, packetHash, packet.getPacketId(), "Payment settled exactly once");
        } catch (SettlementRejectedException ex) {
            return IngestionResult.of(ex.getStatus(), packetHash, packet.getPacketId(), ex.getMessage());
        }
    }

    private IngestionResult validateFreshness(String packetHash, MeshPacket packet, PaymentInstruction instruction) {
        Instant signedAt;
        try {
            signedAt = Instant.ofEpochMilli(instruction.getSignedAt());
        } catch (RuntimeException ex) {
            return IngestionResult.of(IngestionStatus.STALE_REJECTED, packetHash, packet.getPacketId(), "Invalid signedAt timestamp");
        }

        Instant now = Instant.now();
        if (signedAt.isBefore(now.minus(MAX_PAYMENT_AGE)) || signedAt.isAfter(now.plus(MAX_CLOCK_SKEW))) {
            return IngestionResult.of(IngestionStatus.STALE_REJECTED, packetHash, packet.getPacketId(), "Payment instruction is outside the 24 hour freshness window");
        }
        return null;
    }
}
