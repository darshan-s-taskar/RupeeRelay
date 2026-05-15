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
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TamperTest {

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
    void tamperedCiphertextIsRejectedAndLedgerIsUnchanged() {
        MeshPacket packet = validPacket("500.00");
        byte[] bytes = Base64.getDecoder().decode(packet.getCiphertext());
        bytes[bytes.length - 1] = (byte) (bytes[bytes.length - 1] ^ 0x01);
        packet.setCiphertext(Base64.getEncoder().encodeToString(bytes));

        IngestionResult result = bridgeIngestionService.ingest(packet);

        assertThat(result.status()).isEqualTo(IngestionStatus.TAMPERED_REJECTED);
        assertThat(transactionRepository.count()).isZero();
        assertThat(balance("alice@upi")).isEqualByComparingTo("5000.00");
        assertThat(balance("bob@upi")).isEqualByComparingTo("1000.00");
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
