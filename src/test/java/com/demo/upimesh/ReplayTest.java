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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReplayTest {

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
    void replayedCiphertextIsDroppedBeforeSecondSettlement() {
        MeshPacket packet = validPacket("500.00");

        IngestionResult first = bridgeIngestionService.ingest(packet.copy());
        IngestionResult second = bridgeIngestionService.ingest(packet.copy());

        assertThat(first.status()).isEqualTo(IngestionStatus.SETTLED);
        assertThat(second.status()).isEqualTo(IngestionStatus.DUPLICATE_DROPPED);
        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(balance("alice@upi")).isEqualByComparingTo("4500.00");
        assertThat(balance("bob@upi")).isEqualByComparingTo("1500.00");
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
