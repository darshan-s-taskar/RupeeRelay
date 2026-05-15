package com.demo.upimesh.service;

import com.demo.upimesh.model.Account;
import com.demo.upimesh.repository.AccountRepository;
import com.demo.upimesh.repository.TransactionRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
public class DemoDataInitializer {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final PinHasher pinHasher;

    public DemoDataInitializer(AccountRepository accountRepository, TransactionRepository transactionRepository, PinHasher pinHasher) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.pinHasher = pinHasher;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedOnStartup() {
        if (accountRepository.count() == 0) {
            seedAccounts();
        }
    }

    @Transactional
    public void resetDemoData() {
        transactionRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        seedAccounts();
    }

    private void seedAccounts() {
        String pin = pinHasher.hash("1234");
        accountRepository.saveAll(List.of(
                new Account("Alice", "alice@upi", new BigDecimal("5000.00"), pin),
                new Account("Bob", "bob@upi", new BigDecimal("1000.00"), pin),
                new Account("Charlie", "charlie@upi", new BigDecimal("2000.00"), pin)
        ));
    }
}
