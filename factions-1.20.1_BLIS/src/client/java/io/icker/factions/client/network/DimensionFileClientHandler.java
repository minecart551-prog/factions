package io.icker.factions.client.network;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.BitSet;

import io.icker.factions.network.DimensionFilePacket;
import io.icker.factions.network.DimensionNetworkHandler;
import io.icker.factions.util.RegionTransfer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Client-side handler for /f dimension export (S2C download) and import (C2S upload).
 * Writes/reads files under &lt;gameDir&gt;/factions/exports/.
 */
public class DimensionFileClientHandler {
    private static final Map<UUID, FileAssembly> pendingExports = new HashMap<>();

    private static class FileAssembly {
        final int totalChunks;
        final Map<Integer, byte[]> parts = new HashMap<>();
        final BitSet received;
        String filename;

        FileAssembly(int totalChunks) {
            this.totalChunks = totalChunks;
            this.received = new BitSet(totalChunks);
        }

        boolean isComplete() {
            return received.cardinality() == totalChunks;
        }

        byte[] assemble() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (int i = 0; i < totalChunks; i++) {
                byte[] part = parts.get(i);
                if (part != null) baos.writeBytes(part);
            }
            return baos.toByteArray();
        }
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(DimensionNetworkHandler.EXPORT_PACKET_ID,
                (client, handler, buf, responseSender) -> handleExportChunk(buf));
        ClientPlayNetworking.registerGlobalReceiver(DimensionNetworkHandler.IMPORT_REQUEST_PACKET_ID,
                (client, handler, buf, responseSender) -> handleImportRequest(buf));
    }

    public static Path exportsDir() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
                .resolve("factions").resolve("exports");
    }

    private static void handleExportChunk(PacketByteBuf buf) {
        try {
            byte[] nbtBytes = new byte[buf.readableBytes()];
            buf.readBytes(nbtBytes);

            java.io.DataInputStream dis = new java.io.DataInputStream(new java.io.ByteArrayInputStream(nbtBytes));
            net.minecraft.nbt.NbtCompound nbt = net.minecraft.nbt.NbtIo.read(dis);
            if (nbt == null) return;

            DimensionFilePacket packet = DimensionFilePacket.fromNbt(nbt);
            if (packet.sessionId == null || packet.totalChunks < 1
                    || packet.totalChunks > DimensionFilePacket.MAX_CHUNKS
                    || packet.chunkIndex < 0 || packet.chunkIndex >= packet.totalChunks) {
                chat("§cInvalid export packet");
                return;
            }

            FileAssembly assembly = pendingExports.get(packet.sessionId);
            if (assembly == null) {
                if (pendingExports.size() >= 32) pendingExports.clear();
                assembly = new FileAssembly(packet.totalChunks);
                pendingExports.put(packet.sessionId, assembly);
            }
            if (assembly.totalChunks != packet.totalChunks) {
                pendingExports.remove(packet.sessionId);
                return;
            }
            if (packet.filename != null) assembly.filename = packet.filename;
            assembly.parts.put(packet.chunkIndex, packet.data);
            assembly.received.set(packet.chunkIndex);

            if (!assembly.isComplete()) return;
            pendingExports.remove(packet.sessionId);

            String filename = RegionTransfer.sanitizeFilename(assembly.filename);
            if (filename == null) filename = "regions.json";

            String json = RegionTransfer.gunzip(assembly.assemble());
            Path dir = exportsDir();
            Files.createDirectories(dir);
            Path out = dir.resolve(filename);
            Files.writeString(out, json, StandardCharsets.UTF_8);
            chat("§aExported regions to " + out.toAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            chat("§cError writing export file: " + e.getMessage());
        }
    }

    private static void handleImportRequest(PacketByteBuf buf) {
        try {
            byte[] nbtBytes = new byte[buf.readableBytes()];
            buf.readBytes(nbtBytes);

            java.io.DataInputStream dis = new java.io.DataInputStream(new java.io.ByteArrayInputStream(nbtBytes));
            net.minecraft.nbt.NbtCompound nbt = net.minecraft.nbt.NbtIo.read(dis);
            if (nbt == null) return;

            DimensionFilePacket packet = DimensionFilePacket.fromNbt(nbt);
            String filename = RegionTransfer.sanitizeFilename(packet.filename);
            if (filename == null) filename = "regions.json";

            Path file = exportsDir().resolve(filename);
            if (!Files.isRegularFile(file)) {
                chat("§cImport file not found: " + file.toAbsolutePath());
                chat("§7Run /f dimension export first, or place a valid JSON file there.");
                return;
            }

            String json = Files.readString(file, StandardCharsets.UTF_8);
            byte[] compressed = RegionTransfer.gzip(json);
            UUID sessionId = UUID.randomUUID();
            var chunks = RegionTransfer.chunkPayload(sessionId, filename, compressed);

            for (DimensionFilePacket chunk : chunks) {
                sendChunk(DimensionNetworkHandler.IMPORT_PACKET_ID, chunk);
            }
            chat("§eUploading " + filename + " (" + chunks.size() + " chunk(s))...");
        } catch (Exception e) {
            e.printStackTrace();
            chat("§cError reading import file: " + e.getMessage());
        }
    }

    private static void sendChunk(Identifier id, DimensionFilePacket packet) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            net.minecraft.nbt.NbtIo.write(packet.toNbt(), dos);
            byte[] nbtBytes = baos.toByteArray();
            io.netty.buffer.ByteBuf byteBuf = io.netty.buffer.Unpooled.copiedBuffer(nbtBytes);
            PacketByteBuf buf = new PacketByteBuf(byteBuf);
            ClientPlayNetworking.send(id, buf);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void chat(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(Text.literal(message), false);
            }
        });
    }
}
