package io.icker.factions.network;

import io.icker.factions.api.persistents.User;
import io.icker.factions.api.persistents.Faction;
import net.minecraft.nbt.NbtCompound;

/**
 * Packet data for syncing user faction info from server to client
 */
public class UserSyncPacket {
    public boolean inFaction;
    public boolean hasClaims;
    public boolean canEditDimensions;

    public UserSyncPacket(User user, Faction faction) {
        this.inFaction = faction != null;
        this.hasClaims = faction != null && faction.getClaims() != null && !faction.getClaims().isEmpty();
        
        // Can edit dimensions if in a faction with OWNER/COMMANDER/LEADER rank
        this.canEditDimensions = this.inFaction && 
            (user.rank == User.Rank.OWNER || user.rank == User.Rank.COMMANDER || user.rank == User.Rank.LEADER);
    }

    public UserSyncPacket() {
        this.inFaction = false;
        this.hasClaims = false;
        this.canEditDimensions = false;
    }

    /**
     * Serialize user data to NBT
     */
    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        tag.putBoolean("inFaction", this.inFaction);
        tag.putBoolean("hasClaims", this.hasClaims);
        tag.putBoolean("canEditDimensions", this.canEditDimensions);
        return tag;
    }

    /**
     * Deserialize user data from NBT
     */
    public static UserSyncPacket fromNbt(NbtCompound tag) {
        UserSyncPacket packet = new UserSyncPacket();
        packet.inFaction = tag.getBoolean("inFaction");
        packet.hasClaims = tag.getBoolean("hasClaims");
        packet.canEditDimensions = tag.getBoolean("canEditDimensions");
        return packet;
    }
}
