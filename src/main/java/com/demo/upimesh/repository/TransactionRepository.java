package com.demo.upimesh.repository;

import com.demo.upimesh.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    boolean existsByPacketHash(String packetHash);

    long countByPacketHash(String packetHash);
}
