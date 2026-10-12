package io.icker.factions.api.persistents;

import java.util.UUID;

import io.icker.factions.database.Field;

public class RaidRecord {
    @Field("FactionID")
    public UUID factionId;

    @Field("Time")
    public long time;

    public RaidRecord(UUID factionId, long time) {
        this.factionId = factionId;
        this.time = time;
    }

    public RaidRecord() {}
}
