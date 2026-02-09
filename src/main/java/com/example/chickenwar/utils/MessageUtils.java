package com.example.chickenwar.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class MessageUtils {

    public static void broadcast(String message, NamedTextColor color) {
        Bukkit.broadcast(Component.text(message).color(color));
    }

    public static void send(Player player, String message, NamedTextColor color) {
        player.sendMessage(Component.text(message).color(color));
    }

    public static void sendActionBar(Player player, String message) {
        player.sendActionBar(Component.text(message));
    }

    // --- HÀM MỚI ---
    public static void broadcastActionBar(String message) {
        Component comp = Component.text(message);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendActionBar(comp);
        }
    }
}