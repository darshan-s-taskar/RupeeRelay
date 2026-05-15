package com.demo.upimesh.controller;

import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.repository.AccountRepository;
import com.demo.upimesh.repository.TransactionRepository;
import com.demo.upimesh.service.BridgeIngestionService;
import com.demo.upimesh.service.DemoDataInitializer;
import com.demo.upimesh.service.DemoResultLog;
import com.demo.upimesh.service.IdempotencyService;
import com.demo.upimesh.service.IngestionResult;
import com.demo.upimesh.service.IngestionStatus;
import com.demo.upimesh.service.MeshSimulatorService;
import com.demo.upimesh.service.PinHasher;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Map<String, String> DEMO_USERS = Map.of(
            "alice", "1234",
            "bob", "1234",
            "bridge", "1234"
    );

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final HybridCryptoService cryptoService;
    private final MeshSimulatorService meshSimulatorService;
    private final BridgeIngestionService bridgeIngestionService;
    private final DemoDataInitializer demoDataInitializer;
    private final IdempotencyService idempotencyService;
    private final DemoResultLog demoResultLog;
    private final PinHasher pinHasher;

    public ApiController(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            HybridCryptoService cryptoService,
            MeshSimulatorService meshSimulatorService,
            BridgeIngestionService bridgeIngestionService,
            DemoDataInitializer demoDataInitializer,
            IdempotencyService idempotencyService,
            DemoResultLog demoResultLog,
            PinHasher pinHasher
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.cryptoService = cryptoService;
        this.meshSimulatorService = meshSimulatorService;
        this.bridgeIngestionService = bridgeIngestionService;
        this.demoDataInitializer = demoDataInitializer;
        this.idempotencyService = idempotencyService;
        this.demoResultLog = demoResultLog;
        this.pinHasher = pinHasher;
    }

    @PostMapping("/demo/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpSession session) {
        if (request == null || !DEMO_USERS.containsKey(request.username()) || !DEMO_USERS.get(request.username()).equals(request.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "INVALID_LOGIN"));
        }
        session.setAttribute("user", request.username());
        return ResponseEntity.ok(currentState(session, "Logged in as " + request.username()));
    }

    @PostMapping("/demo/logout")
    public DemoStateResponse logout(HttpSession session) {
        session.invalidate();
        return currentState(null, "Logged out");
    }

    @GetMapping("/demo/state")
    public DemoStateResponse state(HttpSession session) {
        return currentState(session, null);
    }

    @PostMapping("/demo/inject")
    public ResponseEntity<?> inject(@RequestBody PaymentRequest request, HttpSession session) {
        ResponseEntity<?> loginRequired = requireLogin(session);
        if (loginRequired != null) {
            return loginRequired;
        }

        long signedAt = request.signedAt() == null ? System.currentTimeMillis() : request.signedAt();
        PaymentInstruction instruction = new PaymentInstruction(
                request.sender(),
                request.receiver(),
                request.amount(),
                pinHasher.hash(request.pin()),
                UUID.randomUUID().toString(),
                signedAt
        );
        MeshPacket packet = new MeshPacket(
                "packet-" + UUID.randomUUID(),
                6,
                System.currentTimeMillis(),
                cryptoService.encrypt(instruction)
        );
        meshSimulatorService.injectIntoAlice(packet);
        return ResponseEntity.ok(currentState(session, "Payment packet injected into phone-alice"));
    }

    @PostMapping("/demo/gossip")
    public ResponseEntity<?> gossip(HttpSession session) {
        ResponseEntity<?> loginRequired = requireLogin(session);
        if (loginRequired != null) {
            return loginRequired;
        }
        MeshSimulatorService.GossipResult result = meshSimulatorService.runGossipRound();
        return ResponseEntity.ok(currentState(session, "Gossip delivered " + result.deliveredCopies() + " new packet copies"));
    }

    @PostMapping("/demo/bridge-upload")
    public ResponseEntity<?> bridgeUpload(HttpSession session) {
        ResponseEntity<?> loginRequired = requireLogin(session);
        if (loginRequired != null) {
            return loginRequired;
        }
        List<IngestionResult> results = meshSimulatorService.uploadFromBridges();
        demoResultLog.setLastResults(results);
        return ResponseEntity.ok(currentState(session, "Bridge upload completed"));
    }

    @PostMapping("/demo/reset")
    public ResponseEntity<?> reset(HttpSession session) {
        ResponseEntity<?> loginRequired = requireLogin(session);
        if (loginRequired != null) {
            return loginRequired;
        }
        demoDataInitializer.resetDemoData();
        meshSimulatorService.resetMesh();
        idempotencyService.clear();
        demoResultLog.clear();
        return ResponseEntity.ok(currentState(session, "Demo reset"));
    }

    @PostMapping("/demo/tamper")
    public ResponseEntity<?> tamper(HttpSession session) {
        ResponseEntity<?> loginRequired = requireLogin(session);
        if (loginRequired != null) {
            return loginRequired;
        }
        MeshPacket tampered = meshSimulatorService.addTamperedPacketToBridge();
        IngestionResult result = tampered == null
                ? IngestionResult.of(IngestionStatus.INVALID_PAYMENT_REJECTED, null, null, "Inject a packet before tampering")
                : bridgeIngestionService.ingest(tampered);
        demoResultLog.setLastResults(List.of(result));
        return ResponseEntity.ok(currentState(session, "Tampered packet submitted to backend"));
    }

    @PostMapping("/demo/stale")
    public ResponseEntity<?> stale(HttpSession session) {
        ResponseEntity<?> loginRequired = requireLogin(session);
        if (loginRequired != null) {
            return loginRequired;
        }

        PaymentInstruction instruction = new PaymentInstruction(
                "alice@upi",
                "bob@upi",
                new BigDecimal("500.00"),
                pinHasher.hash("1234"),
                UUID.randomUUID().toString(),
                Instant.now().minus(Duration.ofHours(25)).toEpochMilli()
        );
        MeshPacket stalePacket = new MeshPacket(
                "stale-" + UUID.randomUUID(),
                1,
                System.currentTimeMillis(),
                cryptoService.encrypt(instruction)
        );
        IngestionResult result = bridgeIngestionService.ingest(stalePacket);
        demoResultLog.setLastResults(List.of(result));
        return ResponseEntity.ok(currentState(session, "Stale packet submitted to backend"));
    }

    @PostMapping("/demo/replay")
    public ResponseEntity<?> replay(HttpSession session) {
        ResponseEntity<?> loginRequired = requireLogin(session);
        if (loginRequired != null) {
            return loginRequired;
        }
        IngestionResult result = meshSimulatorService.replayLastPacket();
        demoResultLog.setLastResults(List.of(result));
        return ResponseEntity.ok(currentState(session, "Replay submitted to backend"));
    }

    @PostMapping("/bridge/ingest")
    public IngestionResult bridgeIngest(@RequestBody MeshPacket packet) {
        IngestionResult result = bridgeIngestionService.ingest(packet);
        demoResultLog.setLastResults(List.of(result));
        return result;
    }

    private ResponseEntity<?> requireLogin(HttpSession session) {
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "LOGIN_REQUIRED"));
        }
        return null;
    }

    private boolean isLoggedIn(HttpSession session) {
        return session != null && session.getAttribute("user") != null;
    }

    private DemoStateResponse currentState(HttpSession session, String message) {
        boolean loggedIn = isLoggedIn(session);
        String user = loggedIn ? session.getAttribute("user").toString() : null;
        return new DemoStateResponse(
                loggedIn,
                user,
                accountRepository.findAll().stream()
                        .sorted(Comparator.comparing(Account::getOwner))
                        .map(AccountView::from)
                        .toList(),
                transactionRepository.findAll().stream()
                        .sorted(Comparator.comparing(Transaction::getSettledAt).reversed())
                        .map(TransactionView::from)
                        .toList(),
                meshSimulatorService.deviceSnapshots(),
                demoResultLog.getLastResults(),
                message
        );
    }

    public record LoginRequest(String username, String password) {
    }

    public record PaymentRequest(String sender, String receiver, BigDecimal amount, String pin, Long signedAt) {
    }

    public record DemoStateResponse(
            boolean loggedIn,
            String user,
            List<AccountView> accounts,
            List<TransactionView> transactions,
            List<MeshSimulatorService.DeviceSnapshot> devices,
            List<IngestionResult> lastResults,
            String message
    ) {
    }

    public record AccountView(Long id, String owner, String upiId, BigDecimal balance, Long version) {
        static AccountView from(Account account) {
            return new AccountView(account.getId(), account.getOwner(), account.getUpiId(), account.getBalance(), account.getVersion());
        }
    }

    public record TransactionView(
            Long id,
            String sender,
            String receiver,
            BigDecimal amount,
            String packetHash,
            String nonce,
            Instant settledAt,
            String status
    ) {
        static TransactionView from(Transaction transaction) {
            return new TransactionView(
                    transaction.getId(),
                    transaction.getSender(),
                    transaction.getReceiver(),
                    transaction.getAmount(),
                    transaction.getPacketHash(),
                    transaction.getNonce(),
                    transaction.getSettledAt(),
                    transaction.getStatus()
            );
        }
    }
}
