package com.example.chickenwar.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public class MessageUtils {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public static void broadcast(String message, NamedTextColor color) {
        Bukkit.broadcast(parse(message).colorIfAbsent(color));
    }

    public static void broadcast(Component message) {
        Bukkit.broadcast(message);
    }

    public static void broadcastLines(List<Component> lines) {
        for (Component line : lines) {
            Bukkit.broadcast(line);
        }
    }

    public static void send(Player player, String message, NamedTextColor color) {
        player.sendMessage(parse(message).colorIfAbsent(color));
    }

    public static void send(Player player, Component message) {
        player.sendMessage(message);
    }

    public static void sendActionBar(Player player, String message) {
        player.sendActionBar(parse(message));
    }

    public static void sendActionBar(Player player, Component message) {
        player.sendActionBar(message);
    }

    public static void broadcastActionBar(String message) {
        Component comp = parse(message);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendActionBar(comp);
        }
    }

    private static Component parse(String message) {
        return MINI_MESSAGE.deserialize(message == null ? "" : message);
    }
}