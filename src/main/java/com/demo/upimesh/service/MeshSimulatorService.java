package com.demo.upimesh.service;

import com.demo.upimesh.model.MeshPacket;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MeshSimulatorService {

    private static final String ALICE_DEVICE = "phone-alice";

    private final BridgeIngestionService bridgeIngestionService;
    private final Map<String, VirtualDevice> devices = new LinkedHashMap<>();
    private MeshPacket lastInjectedPacket;

    public MeshSimulatorService(BridgeIngestionService bridgeIngestionService) {
        this.bridgeIngestionService = bridgeIngestionService;
        resetMesh();
    }

    public synchronized void resetMesh() {
        devices.clear();
        devices.put("phone-alice", new VirtualDevice("phone-alice", false));
        devices.put("phone-bob", new VirtualDevice("phone-bob", false));
        devices.put("phone-stranger-1", new VirtualDevice("phone-stranger-1", false));
        devices.put("phone-stranger-2", new VirtualDevice("phone-stranger-2", false));
        devices.put("phone-bridge", new VirtualDevice("phone-bridge", true));
        lastInjectedPacket = null;
    }

    public synchronized MeshPacket injectIntoAlice(MeshPacket packet) {
        devices.get(ALICE_DEVICE).addPacket(packet);
        lastInjectedPacket = packet.copy();
        return packet.copy();
    }

    public synchronized GossipResult runGossipRound() {
        Map<String, List<MeshPacket>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, VirtualDevice> entry : devices.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().packets());
        }

        int delivered = 0;
        for (VirtualDevice device : devices.values()) {
            device.decrementTtlForHeldPackets();
        }

        for (Map.Entry<String, List<MeshPacket>> source : snapshot.entrySet()) {
            for (MeshPacket packet : source.getValue()) {
                if (packet.getTtl() <= 0) {
                    continue;
                }
                MeshPacket forwarded = packet.copyWithTtl(packet.getTtl() - 1);
                for (Map.Entry<String, VirtualDevice> target : devices.entrySet()) {
                    if (!target.getKey().equals(source.getKey()) && target.getValue().addPacket(forwarded)) {
                        delivered++;
                    }
                }
            }
        }
        return new GossipResult(delivered);
    }

    public synchronized List<IngestionResult> uploadFromBridges() {
        List<IngestionResult> results = new ArrayList<>();
        for (VirtualDevice device : devices.values()) {
            if (device.isHasInternet()) {
                for (MeshPacket packet : device.packets()) {
                    results.add(bridgeIngestionService.ingest(packet));
                }
            }
        }
        if (results.isEmpty()) {
            results.add(IngestionResult.of(IngestionStatus.INVALID_PAYMENT_REJECTED, null, null, "No packets reached an internet bridge"));
        }
        return results;
    }

    public synchronized IngestionResult replayLastPacket() {
        if (lastInjectedPacket == null) {
            return IngestionResult.of(IngestionStatus.INVALID_PAYMENT_REJECTED, null, null, "No packet has been injected yet");
        }
        return bridgeIngestionService.ingest(lastInjectedPacket.copy());
    }

    public synchronized MeshPacket addTamperedPacketToBridge() {
        if (lastInjectedPacket == null) {
            return null;
        }

        byte[] bytes = Base64.getDecoder().decode(lastInjectedPacket.getCiphertext());
        bytes[bytes.length - 1] = (byte) (bytes[bytes.length - 1] ^ 0x01);

        MeshPacket tampered = new MeshPacket(
                "tampered-" + UUID.randomUUID(),
                lastInjectedPacket.getTtl(),
                System.currentTimeMillis(),
                Base64.getEncoder().encodeToString(bytes)
        );
        devices.get("phone-bridge").addPacket(tampered);
        return tampered.copy();
    }

    public synchronized List<DeviceSnapshot> deviceSnapshots() {
        List<DeviceSnapshot> snapshots = new ArrayList<>();
        for (VirtualDevice device : devices.values()) {
            snapshots.add(new DeviceSnapshot(
                    device.getDeviceId(),
                    device.isHasInternet(),
                    device.packetCount(),
                    device.packetSummaries()
            ));
        }
        return snapshots;
    }

    public record GossipResult(int deliveredCopies) {
    }

    public record DeviceSnapshot(
            String deviceId,
            boolean hasInternet,
            int packetCount,
            List<VirtualDevice.PacketSummary> packets
    ) {
    }
}
