package com.example.chickenwar.utils;

import com.example.chickenwar.ChickenWarPlugin;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Locale;

public final class SoundUtil {

    private SoundUtil() {
    }

    public static void playConfiguredToPlayers(ChickenWarPlugin plugin, String soundPath, Collection<? extends Player> players) {
        String raw = plugin.getConfigManager().getRawString(soundPath, "");
        SoundPayload payload = parsePayload(plugin, raw);
        if (payload == null) {
            return;
        }

        for (Player player : players) {
            player.playSound(player.getLocation(), payload.sound(), payload.volume(), payload.pitch());
        }
    }

    public static void playConfiguredToPlayer(ChickenWarPlugin plugin, String soundPath, Player player) {
        String raw = plugin.getConfigManager().getRawString(soundPath, "");
        SoundPayload payload = parsePayload(plugin, raw);
        if (payload == null) {
            return;
        }
        player.playSound(player.getLocation(), payload.sound(), payload.volume(), payload.pitch());
    }

    public static void playConfiguredAtLocation(ChickenWarPlugin plugin, String soundPath, Location location) {
        String raw = plugin.getConfigManager().getRawString(soundPath, "");
        SoundPayload payload = parsePayload(plugin, raw);
        if (payload == null || location.getWorld() == null) {
            return;
        }
        location.getWorld().playSound(location, payload.sound(), payload.volume(), payload.pitch());
    }

    public static void playConfiguredToPlayersWithPitch(ChickenWarPlugin plugin, String soundPath, Collection<? extends Player> players, float pitch) {
        String raw = plugin.getConfigManager().getRawString(soundPath, "");
        SoundPayload payload = parsePayload(plugin, raw);
        if (payload == null) {
            return;
        }

        for (Player player : players) {
            player.playSound(player.getLocation(), payload.sound(), payload.volume(), pitch);
        }
    }

    private static SoundPayload parsePayload(ChickenWarPlugin plugin, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String[] split = raw.split(";");
        if (split.length < 1) {
            return null;
        }

        Sound sound = parseSound(plugin, split[0]);
        if (sound == null) {
            return null;
        }

        float volume = parseFloat(split, 1, 1.0f);
        float pitch = parseFloat(split, 2, 1.0f);
        return new SoundPayload(sound, volume, pitch);
    }

    private static float parseFloat(String[] split, int index, float defaultValue) {
        if (index >= split.length) {
            return defaultValue;
        }

        try {
            return Float.parseFloat(split[index]);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static Sound parseSound(ChickenWarPlugin plugin, String token) {
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        // First pass: normalize to enum-style token so both ENTITY_X and entity.x map correctly.
        String enumCandidate = toEnumCandidate(trimmed);
        try {
            return Sound.valueOf(enumCandidate);
        } catch (IllegalArgumentException ignored) {
            // Continue with registry lookup as a compatibility fallback.
        }

        String keyCandidate = toNamespacedKeyCandidate(trimmed);
        org.bukkit.NamespacedKey namespacedKey = org.bukkit.NamespacedKey.fromString(keyCandidate);
        Sound sound = namespacedKey == null ? null : Registry.SOUNDS.get(namespacedKey);
        if (sound == null) {
            plugin.getLogger().warning("Invalid configured sound token: " + token);
        }
        return sound;
    }

    private static String toEnumCandidate(String token) {
        String sanitized = token.trim().toUpperCase(Locale.ROOT);
        if (sanitized.startsWith("MINECRAFT:")) {
            sanitized = sanitized.substring("MINECRAFT:".length());
        }
        return sanitized.replace('.', '_').replace('-', '_');
    }

    private static String toNamespacedKeyCandidate(String token) {
        String normalized = token.trim().toLowerCase(Locale.ROOT).replace('_', '.');
        if (normalized.startsWith("minecraft:")) {
            return normalized;
        }
        return "minecraft:" + normalized;
    }

    private record SoundPayload(Sound sound, float volume, float pitch) {
    }
}
