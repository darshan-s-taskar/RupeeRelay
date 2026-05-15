package com.demo.upimesh.service;

import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.repository.AccountRepository;
import com.demo.upimesh.repository.TransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class SettlementService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public SettlementService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public Transaction settle(PaymentInstruction instruction, String packetHash) {
        if (instruction.getAmount() == null || instruction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new SettlementRejectedException(IngestionStatus.INVALID_PAYMENT_REJECTED, "Amount must be greater than zero");
        }

        Account sender = accountRepository.findByUpiId(instruction.getSenderUpiId())
                .orElseThrow(() -> new SettlementRejectedException(IngestionStatus.UNKNOWN_ACCOUNT_REJECTED, "Sender account not found"));
        Account receiver = accountRepository.findByUpiId(instruction.getReceiverUpiId())
                .orElseThrow(() -> new SettlementRejectedException(IngestionStatus.UNKNOWN_ACCOUNT_REJECTED, "Receiver account not found"));

        if (sender.getBalance().compareTo(instruction.getAmount()) < 0) {
            throw new SettlementRejectedException(IngestionStatus.INSUFFICIENT_FUNDS_REJECTED, "Insufficient sender balance");
        }

        sender.setBalance(sender.getBalance().subtract(instruction.getAmount()));
        receiver.setBalance(receiver.getBalance().add(instruction.getAmount()));

        Transaction transaction = new Transaction(
                sender.getUpiId(),
                receiver.getUpiId(),
                instruction.getAmount(),
                packetHash,
                instruction.getNonce(),
                Instant.now(),
                IngestionStatus.SETTLED.name()
        );

        try {
            return transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException ex) {
            throw new SettlementRejectedException(IngestionStatus.DUPLICATE_DROPPED, "Packet hash already exists in ledger");
        }
    }
}
