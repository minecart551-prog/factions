package io.icker.factions.command;

import java.util.List;
import java.util.stream.Collectors;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.icker.factions.FactionsMod;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.User;
import io.icker.factions.util.Command;
import io.icker.factions.util.Message;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.UserCache;
import net.minecraft.util.Util;

public class InfoCommand implements Command {
    private int self(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        User user = Command.getUser(player);
        if (!user.isInFaction()) {
            new Message("Command can only be used whilst in a faction").fail().send(player, false);
            return 0;
        }

        return info(player, user.getFaction());
    }

    private int any(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String factionName = StringArgumentType.getString(context, "faction");

        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        Faction faction = Faction.getByName(factionName);
        if (faction == null) {
            new Message("Faction does not exist").fail().send(player, false);
            return 0;
        }

        return info(player, faction);
    }

    public static int info(ServerPlayerEntity player, Faction faction) {
        List<User> users = faction.getUsers();

        UserCache cache = player.getServer().getUserCache();
        String owner = Formatting.WHITE + users.stream().filter(u -> u.rank == User.Rank.OWNER)
                .map(user -> cache.getByUuid(user.getID())
                        .orElse(new GameProfile(Util.NIL_UUID, "{Uncached Player}")).getName())
                .collect(Collectors.joining(", "));

        String usersList = users.stream()
                .map(user -> {
                    String name = cache.getByUuid(user.getID())
                            .orElse(new GameProfile(Util.NIL_UUID, "{Uncached Player}")).getName();
                    int power = (int) (user.getPower() * user.getActivityMultiplier());
                    return name + Formatting.GRAY + " (" + power + ")" + Formatting.WHITE;
                })
                .collect(Collectors.joining(", "));

        String mutualAllies = faction.getMutualAllies().stream().map(rel -> Faction.get(rel.target))
                .map(fac -> fac.getColor() + fac.getName())
                .collect(Collectors.joining(Formatting.GRAY + ", "));

        String mutualFriendly = faction.getMutualFriendly().stream()
                .map(rel -> Faction.get(rel.target))
                .map(fac -> fac.getColor() + fac.getName())
                .collect(Collectors.joining(Formatting.GRAY + ", "));

        String enemiesWith = Formatting.GRAY + faction.getEnemiesWith().stream()
                .map(rel -> Faction.get(rel.target)).map(fac -> fac.getColor() + fac.getName())
                .collect(Collectors.joining(Formatting.GRAY + ", "));

        int maxPower = faction.calculateMaxPower();

        // Show faction name, generate the ---
        int numDashes = 32 - faction.getName().length();
        String dashes =
                new StringBuilder("--------------------------------").substring(0, numDashes / 2);

        new Message(Formatting.BLACK + dashes + "[ " + faction.getColor() + faction.getName()
                + Formatting.BLACK + " ]" + dashes).send(player, false);
        
        // Show Admin protection status (only if ON)
        if (faction.isAdminProtected()) {
            new Message(Formatting.LIGHT_PURPLE + "★ Admin Protected ★").send(player, false);
        }
        
        // Show Description
        new Message(Formatting.GOLD + "Description: ")
                .add(Formatting.WHITE + faction.getDescription()).send(player, false);

        // Show faction owner
        new Message(Formatting.GOLD + "Owner: ").add(Formatting.WHITE + owner).send(player, false);

        // Show member list
        new Message(Formatting.GOLD + "Members (" + Formatting.WHITE.toString() + users.size()
                + Formatting.GOLD.toString() + "): ").add(usersList).send(player, false);

        // Build total power breakdown hover text with colors
        int maxMemberPower = users.size() * FactionsMod.CONFIG.POWER.MEMBER;
        long daysSinceSacrifice = faction.getDaysSinceLastSacrifice();
        String sacrificeInfo = daysSinceSacrifice < 0 ? "never" : daysSinceSacrifice + " days ago";
        long daysSinceWarKill = faction.getDaysSinceLastWarKill();
        String warKillInfo = daysSinceWarKill < 0 ? "never" : daysSinceWarKill + " days ago";
        long daysSinceFameGain = faction.getDaysSinceLastFameGain();
        String fameInfo = daysSinceFameGain < 0 ? "never" : daysSinceFameGain + " days ago";

        MutableText powerHover = Text.empty();
        if (FactionsMod.CONFIG.POWER.BASE > 0) {
            powerHover.append(Text.literal("Base: ").formatted(Formatting.GOLD))
                    .append(Text.literal(String.valueOf(FactionsMod.CONFIG.POWER.BASE)).formatted(Formatting.GREEN))
                    .append("\n");
        }
        if (faction.getAdminPower() != 0) {
            powerHover.append(Text.literal("Admin: ").formatted(Formatting.GOLD))
                    .append(Text.literal(String.valueOf(faction.getAdminPower())).formatted(Formatting.LIGHT_PURPLE))
                    .append("\n");
        }
        powerHover.append(Text.literal("Player: ").formatted(Formatting.GOLD))
                .append(Text.literal(faction.getMemberPower() + " / " + maxMemberPower).formatted(Formatting.GREEN))
                .append("\n");
        powerHover.append(Text.literal("Wealth: ").formatted(Formatting.GOLD))
                .append(Text.literal(faction.getWealthPower() + " / " + FactionsMod.CONFIG.POWER.WEALTH.MAX_VALUE).formatted(Formatting.YELLOW))
                .append(Text.literal(" (last sacrifice: " + sacrificeInfo + ")").formatted(Formatting.GRAY))
                .append("\n");
        powerHover.append(Text.literal("War: ").formatted(Formatting.GOLD))
                .append(Text.literal(faction.getWarPower() + " / " + FactionsMod.CONFIG.POWER.WAR.MAX_VALUE).formatted(Formatting.RED))
                .append(Text.literal(" (last kill: " + warKillInfo + ")").formatted(Formatting.GRAY))
                .append("\n");
        powerHover.append(Text.literal("Fame: ").formatted(Formatting.GOLD))
                .append(Text.literal(faction.getFamePower() + " / " + FactionsMod.CONFIG.POWER.FAME.MAX_VALUE).formatted(Formatting.AQUA))
                .append(Text.literal(" (last gain: " + fameInfo + ")").formatted(Formatting.GRAY));
        int vassalBonus = faction.getVassalPowerBonus();
        if (vassalBonus > 0) {
            powerHover.append("\n");
            powerHover.append(Text.literal("Vassal: ").formatted(Formatting.GOLD))
                    .append(Text.literal("+" + vassalBonus).formatted(Formatting.LIGHT_PURPLE));
        }

        new Message(Formatting.GOLD + "Total Power: ").add(Formatting.GREEN.toString()
                + faction.getPower() + slash() + maxPower)
                .hover(powerHover).send(player, false);

        // Show required power
        int claimCount = faction.getClaims().size();
        int claimWeight = FactionsMod.CONFIG.POWER.CLAIM_WEIGHT;
        int requiredPower = claimCount * claimWeight;

        MutableText requiredHover = Text.empty()
                .append(Text.literal("Claims: ").formatted(Formatting.GOLD))
                .append(Text.literal(String.valueOf(claimCount)).formatted(Formatting.GREEN))
                .append("\n")
                .append(Text.literal("Claim Weight: ").formatted(Formatting.GOLD))
                .append(Text.literal(String.valueOf(claimWeight)).formatted(Formatting.GREEN))
                .append("\n")
                .append(Text.literal("Formula: ").formatted(Formatting.GRAY))
                .append(Text.literal(claimCount + " × " + claimWeight + " = " + requiredPower).formatted(Formatting.WHITE));

        boolean powerInsufficient = faction.getPower() < requiredPower;
        new Message(Formatting.GOLD + "Required Power: ").add(
                (powerInsufficient ? Formatting.RED : Formatting.GREEN).toString() + requiredPower)
                .hover(requiredHover).send(player, false);

        if (powerInsufficient && !faction.isAdminProtected()) {
            if (FactionsMod.CONFIG.CLAIM_PROTECTION) {
                new Message(Formatting.RED + "Not enough power to sustain the claims! Protections are currently off!").send(player, false);
            }
            if (FactionsMod.CONFIG.POWER.CLAIM_DECAY_ENABLED) {
                int decaySeconds = FactionsMod.CONFIG.POWER.DECAY_CHECK_TICKS / 20;
                String decayRate = decaySeconds >= 60
                        ? (decaySeconds / 60) + " minute" + (decaySeconds / 60 == 1 ? "" : "s")
                        : decaySeconds + " second" + (decaySeconds == 1 ? "" : "s");
                new Message(Formatting.RED + "Farthest claims will decay automatically every " + decayRate + ".").send(player, false);
            }
        }

        // Show vassal information
        if (FactionsMod.CONFIG.VASSAL.ENABLED) {
            if (faction.isVassal()) {
                Faction overlord = faction.getOverlord();
                new Message(Formatting.LIGHT_PURPLE + "Overlord: ")
                        .add(overlord.getColor() + overlord.getName())
                        .send(player, false);
            }

            List<Faction> vassals = faction.getVassals();
            if (!vassals.isEmpty()) {
                String vassalList = vassals.stream()
                        .map(v -> v.getColor() + v.getName())
                        .collect(Collectors.joining(Formatting.GRAY + ", "));
                int vassalPowerBonus = faction.getVassalPowerBonus();
                new Message(Formatting.LIGHT_PURPLE + "Vassals (" + Formatting.WHITE + vassals.size()
                        + Formatting.LIGHT_PURPLE + "): ").add(vassalList)
                        .hover("Vassal Power Bonus: +" + vassalPowerBonus)
                        .send(player, false);
            }
        }

        // Show active blessings from God's Blessings system
        if (FactionsMod.CONFIG.GODS != null && FactionsMod.CONFIG.GODS.ENABLED) {
            List<Faction.ActiveBlessing> blessings = faction.getActiveBlessings();
            if (!blessings.isEmpty()) {
                String blessingNames = blessings.stream()
                        .map(b -> Formatting.GOLD + b.godName)
                        .collect(Collectors.joining(Formatting.GRAY + ", "));

                MutableText blessingsHover = Text.empty();
                for (int i = 0; i < blessings.size(); i++) {
                    Faction.ActiveBlessing blessing = blessings.get(i);
                    long remainingSeconds = (blessing.expiresAt - System.currentTimeMillis()) / 1000;
                    String effectName = blessing.effect.replace("minecraft:", "");
                    int level = blessing.amplifier + 1;

                    if (i > 0) blessingsHover.append("\n");
                    blessingsHover.append(Text.literal(blessing.godName + ": ").formatted(Formatting.GOLD))
                            .append(Text.literal(effectName + " " + level).formatted(Formatting.YELLOW))
                            .append(Text.literal(" (" + remainingSeconds + "s remaining)").formatted(Formatting.GRAY));
                }

                new Message(Formatting.GOLD + "Active Blessings (" + Formatting.WHITE + blessings.size()
                        + Formatting.GOLD + "): ").add(blessingNames)
                        .hover(blessingsHover)
                        .send(player, false);
            }
        }

        // Show faction relationships
        new Message(Formatting.GREEN + "Allies (" + Formatting.WHITE
                + faction.getMutualAllies().size() + Formatting.GREEN + "): ").add(mutualAllies)
                        .send(player, false);
        new Message(Formatting.AQUA + "Friendly (" + Formatting.WHITE
                + faction.getMutualFriendly().size() + Formatting.AQUA + "): ").add(mutualFriendly)
                        .send(player, false);
        new Message(Formatting.RED + "Enemies (" + Formatting.WHITE
                + faction.getEnemiesWith().size() + Formatting.RED + "): ").add(enemiesWith)
                        .send(player, false);

        // Add compat config info
        String compatLevel = FactionsMod.CONFIG.RELATIONSHIPS.COMPAT_SKILL_DAMAGE_PROTECTION_FOR.toString();
        new Message(Formatting.GRAY + "Compatibility Settings:")
                .send(player, false);
        new Message(Formatting.GRAY + "- Skill Damage Protection for: " + Formatting.YELLOW + compatLevel )
                .hover("Skill Damages are disabled if your faction relationship is equal or higher than current setting")
                .send(player, false);

        return 1;
    }

    private static String slash() {
        return Formatting.GRAY + " / " + Formatting.GREEN;
    }

    public LiteralCommandNode<ServerCommandSource> getNode() {
        return CommandManager.literal("info").requires(Requires.hasPerms("factions.info", 0))
                .executes(this::self)
                .then(CommandManager.argument("faction", StringArgumentType.greedyString())
                        .requires(Requires.hasPerms("factions.info.other", 0))
                        .suggests(Suggests.allFactions()).executes(this::any))
                .build();
    }
}