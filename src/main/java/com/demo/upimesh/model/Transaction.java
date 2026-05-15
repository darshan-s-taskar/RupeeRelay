package com.demo.upimesh.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "transactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_transactions_packet_hash", columnNames = "packet_hash")
)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sender;

    @Column(nullable = false)
    private String receiver;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "packet_hash", nullable = false, length = 64)
    private String packetHash;

    @Column(nullable = false)
    private String nonce;

    @Column(nullable = false)
    private Instant settledAt;

    @Column(nullable = false)
    private String status;

    protected Transaction() {
    }

    public Transaction(String sender, String receiver, BigDecimal amount, String packetHash, String nonce, Instant settledAt, String status) {
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.packetHash = packetHash;
        this.nonce = nonce;
        this.settledAt = settledAt;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getPacketHash() {
        return packetHash;
    }

    public String getNonce() {
        return nonce;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public String getStatus() {
        return status;
    }
}
