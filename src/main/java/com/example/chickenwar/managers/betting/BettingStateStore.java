package com.example.chickenwar.managers.betting;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BettingStateStore {

    private BettingStateStore() {
    }

    public static BettingState load(File file) {
        if (!file.exists()) {
            return BettingState.empty();
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        long jackpot = Math.max(0L, yaml.getLong("jackpot", 0L));
        long currentInitialMaxBet = Math.max(0L, yaml.getLong("current-initial-max-bet", 0L));
        boolean hasUnfinishedRound = yaml.getBoolean("has-unfinished-round", false);

        Map<UUID, Long> redBets = readMap(yaml.getConfigurationSection("red-bets"));
        Map<UUID, Long> blueBets = readMap(yaml.getConfigurationSection("blue-bets"));
        Map<UUID, Long> pendingClaims = readMap(yaml.getConfigurationSection("pending-claims"));

        return new BettingState(jackpot, currentInitialMaxBet, hasUnfinishedRound, redBets, blueBets, pendingClaims);
    }

    public static void save(File file, BettingState state) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("jackpot", state.jackpot());
        yaml.set("current-initial-max-bet", state.currentInitialMaxBet());
        yaml.set("has-unfinished-round", state.hasUnfinishedRound());

        writeMap(yaml, "red-bets", state.redBets());
        writeMap(yaml, "blue-bets", state.blueBets());
        writeMap(yaml, "pending-claims", state.pendingClaims());

        file.getParentFile().mkdirs();
        try {
            yaml.save(file);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot save betting state", e);
        }
    }

    private static Map<UUID, Long> readMap(ConfigurationSection section) {
        Map<UUID, Long> out = new HashMap<>();
        if (section == null) {
            return out;
        }

        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                long amount = Math.max(0L, section.getLong(key));
                if (amount > 0) {
                    out.put(id, amount);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid UUID entries.
            }
        }
        return out;
    }

    private static void writeMap(YamlConfiguration yaml, String path, Map<UUID, Long> values) {
        for (Map.Entry<UUID, Long> entry : values.entrySet()) {
            yaml.set(path + "." + entry.getKey(), entry.getValue());
        }
    }

    public record BettingState(
            long jackpot,
            long currentInitialMaxBet,
            boolean hasUnfinishedRound,
            Map<UUID, Long> redBets,
            Map<UUID, Long> blueBets,
            Map<UUID, Long> pendingClaims
    ) {
        public static BettingState empty() {
            return new BettingState(0L, 0L, false, new HashMap<>(), new HashMap<>(), new HashMap<>());
        }
    }
}

