package com.demo.upimesh.service;

import com.demo.upimesh.model.MeshPacket;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VirtualDevice {

    private final String deviceId;
    private final boolean hasInternet;
    private final Map<String, MeshPacket> packetsByCiphertext = new LinkedHashMap<>();

    public VirtualDevice(String deviceId, boolean hasInternet) {
        this.deviceId = deviceId;
        this.hasInternet = hasInternet;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public boolean isHasInternet() {
        return hasInternet;
    }

    public boolean addPacket(MeshPacket packet) {
        return packetsByCiphertext.putIfAbsent(packet.getCiphertext(), packet.copy()) == null;
    }

    public List<MeshPacket> packets() {
        return packetsByCiphertext.values().stream().map(MeshPacket::copy).toList();
    }

    public int packetCount() {
        return packetsByCiphertext.size();
    }

    public void decrementTtlForHeldPackets() {
        for (MeshPacket packet : packetsByCiphertext.values()) {
            if (packet.getTtl() > 0) {
                packet.setTtl(packet.getTtl() - 1);
            }
        }
    }

    public List<PacketSummary> packetSummaries() {
        List<PacketSummary> summaries = new ArrayList<>();
        for (MeshPacket packet : packetsByCiphertext.values()) {
            summaries.add(new PacketSummary(packet.getPacketId(), packet.getTtl(), packet.getCreatedAt()));
        }
        return summaries;
    }

    public record PacketSummary(String packetId, int ttl, long createdAt) {
    }
}
