package io.icker.factions.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import io.icker.factions.api.persistents.User;
import io.icker.factions.api.persistents.Faction;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Handles network communication for dimension blacklist selections
 */
public class DimensionNetworkHandler {
    public static final Identifier COMMIT_PACKET_ID = new Identifier("factions", "dimension_commit");

    public static void registerHandlers() {
        // Register the server-side packet receiver
        ServerPlayNetworking.registerGlobalReceiver(COMMIT_PACKET_ID, 
            (server, player, handler, buf, responseSender) -> {
                handleCommitPacket(server, player, buf);
            });
    }

    /**
     * Handle incoming commit packet from client
     */
    private static void handleCommitPacket(net.minecraft.server.MinecraftServer server, ServerPlayerEntity player, 
                                           PacketByteBuf buf) {
        // Queue the operation on the server thread
        server.execute(() -> {
            try {
                // Read the NBT data
                byte[] nbtBytes = new byte[buf.readableBytes()];
                buf.readBytes(nbtBytes);
                
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(nbtBytes));
                var nbtResult = NbtIo.read(dis);
                if (nbtResult != null) {
                    DimensionCommitPacket packet = DimensionCommitPacket.fromNbt(nbtResult);
                    
                    // Get faction and save dimensions
                    User user = User.get(player.getUuid());
                    Faction faction = user.getFaction();
                    
                    if (faction != null) {
                        faction.dimensionBlacklist.addAll(packet.dimensions);
                        faction.save();
                        player.sendMessage(
                            net.minecraft.text.Text.literal("§6Dimension selections committed!"), false);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Serialize dimensions to bytes for network transmission
     */
    public static byte[] serializeDimensions(java.util.List<io.icker.factions.api.persistents.BlacklistedDimension> dimensions) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            DimensionCommitPacket packet = new DimensionCommitPacket(dimensions);
            NbtIo.write(packet.toNbt(), dos);
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }
}
