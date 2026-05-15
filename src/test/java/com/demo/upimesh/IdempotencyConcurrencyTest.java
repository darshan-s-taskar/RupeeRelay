package com.demo.upimesh;

import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.repository.AccountRepository;
import com.demo.upimesh.repository.TransactionRepository;
import com.demo.upimesh.service.BridgeIngestionService;
import com.demo.upimesh.service.DemoDataInitializer;
import com.demo.upimesh.service.IdempotencyService;
import com.demo.upimesh.service.IngestionResult;
import com.demo.upimesh.service.IngestionStatus;
import com.demo.upimesh.service.PinHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class IdempotencyConcurrencyTest {

    @Autowired
    private BridgeIngestionService bridgeIngestionService;

    @Autowired
    private HybridCryptoService cryptoService;

    @Autowired
    private PinHasher pinHasher;

    @Autowired
    private DemoDataInitializer demoDataInitializer;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void resetDemo() {
        demoDataInitializer.resetDemoData();
        idempotencyService.clear();
    }

    @Test
    void concurrentUploadsOfSamePacketSettleOnlyOnce() throws Exception {
        MeshPacket packet = validPacket("500.00");
        int attempts = 40;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<IngestionResult>> futures = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                futures.add(executor.submit(uploadTask(packet, ready, start)));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<IngestionResult> results = new ArrayList<>();
            for (Future<IngestionResult> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            assertThat(results).filteredOn(result -> result.status() == IngestionStatus.SETTLED).hasSize(1);
            assertThat(results).filteredOn(result -> result.status() == IngestionStatus.DUPLICATE_DROPPED).hasSize(attempts - 1);
            assertThat(transactionRepository.count()).isEqualTo(1);
            assertThat(balance("alice@upi")).isEqualByComparingTo("4500.00");
            assertThat(balance("bob@upi")).isEqualByComparingTo("1500.00");
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<IngestionResult> uploadTask(MeshPacket packet, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await();
            return bridgeIngestionService.ingest(packet.copy());
        };
    }

    private MeshPacket validPacket(String amount) {
        PaymentInstruction instruction = new PaymentInstruction(
                "alice@upi",
                "bob@upi",
                new BigDecimal(amount),
                pinHasher.hash("1234"),
                UUID.randomUUID().toString(),
                System.currentTimeMillis()
        );
        return new MeshPacket("packet-" + UUID.randomUUID(), 6, System.currentTimeMillis(), cryptoService.encrypt(instruction));
    }

    private BigDecimal balance(String upiId) {
        Account account = accountRepository.findByUpiId(upiId).orElseThrow();
        return account.getBalance();
    }
}
