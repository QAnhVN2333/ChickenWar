package com.example.cockfight;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CockfightCommand implements CommandExecutor {

    private final CockfightPlugin plugin;
    private final GameManager gameManager;

    public CockfightCommand(CockfightPlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) return false;

        // Lệnh Reload
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage(Component.text("Đã reload config thành công!").color(NamedTextColor.GREEN));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Lệnh này chỉ dành cho người chơi.");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "build":
                player.sendMessage(Component.text("Đang xây dựng đấu trường...").color(NamedTextColor.YELLOW));
                gameManager.buildArena(player.getLocation());
                break;
            case "start":
                gameManager.startFight();
                break;
            case "restart": // LỆNH MỚI
                gameManager.restartMatch();
                break;
            case "end":
                gameManager.endFight();
                break;
            default:
                player.sendMessage(Component.text("Sai cú pháp! /cockfight <build|start|restart|end|reload>").color(NamedTextColor.RED));
        }
        return true;
    }
}