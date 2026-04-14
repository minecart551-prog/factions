# Fame Power API - Integration Guide

This guide explains how to integrate the Fame Power system into external mods (e.g., dungeon mods).

## API Method

```java
io.icker.factions.FactionsMod.addFamePower(UUID playerUUID, int amount)
```

## Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `playerUUID` | `UUID` | The UUID of the player who earned the fame |
| `amount` | `int` | Fame power to grant |

## Return Value

| Value | Meaning |
|-------|---------|
| `> 0` | Actual amount added to faction (may be less if near max) |
| `0` | Player has no faction, or faction already at max fame |

## Example Usage

```java
import io.icker.factions.FactionsMod;
import java.util.UUID;

// When player completes a dungeon:
public void onDungeonComplete(ServerPlayerEntity player, String dungeonDifficulty) {
    int fameReward = switch (dungeonDifficulty) {
        case "easy" -> 5;
        case "normal" -> 10;
        case "hard" -> 20;
        case "legendary" -> 35;
        default -> 5;
    };

    int added = FactionsMod.addFamePower(player.getUuid(), fameReward);

    if (added > 0) {
        // Notify player their faction gained fame
        player.sendMessage(Text.literal("Your faction gained " + added + " fame power!"));
    }
}
```

## Configuration (Server-side)

Server admins can adjust these values in `config/factions.json`:

```json
"fame": {
    "maxValue": 100,
    "decayPerDay": 5
}
```

| Setting | Default | Description |
|---------|---------|-------------|
| `maxValue` | 100 | Maximum fame a faction can accumulate |
| `decayPerDay` | 5 | Fame lost per real day since last gain |

## Dependency Setup

Add Factions as a dependency in your `fabric.mod.json`:

```json
{
    "depends": {
        "factions": "*"
    }
}
```

## Optional: Check if Factions is Loaded

```java
import net.fabricmc.loader.api.FabricLoader;

public void grantFame(ServerPlayerEntity player, int amount) {
    if (FabricLoader.getInstance().isModLoaded("factions")) {
        FactionsMod.addFamePower(player.getUuid(), amount);
    }
}
```

## How Fame Works

- Fame contributes to faction's **total power**
- Higher power allows factions to **claim more territory**
- Fame **decays over time** if the faction stops earning it
- Players can view fame in `/f info` (hover over Total Power)
