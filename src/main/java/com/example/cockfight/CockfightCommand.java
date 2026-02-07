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

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage(Component.text("Đã reload config!").color(NamedTextColor.GREEN));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Lệnh này chỉ dành cho người chơi.");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "build":
                gameManager.buildArena(player.getLocation());
                break;
            case "start":
                gameManager.startFight();
                break;
            case "restart":
                gameManager.restartMatch();
                break;
            case "end":
                gameManager.endFight();
                break;
            case "bet":
                // Cú pháp: /cockfight bet <red/blue> <amount>
                if (args.length < 3) {
                    player.sendMessage(Component.text("Sai cú pháp! Dùng: /cockfight bet <red/blue> <số tiền>").color(NamedTextColor.RED));
                    return true;
                }
                String side = args[1];
                double amount;
                try {
                    amount = Double.parseDouble(args[2]);
                    if (amount <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("Số tiền không hợp lệ!").color(NamedTextColor.RED));
                    return true;
                }

                if (!side.equalsIgnoreCase("red") && !side.equalsIgnoreCase("blue")) {
                    player.sendMessage(Component.text("Chỉ được chọn red (đỏ) hoặc blue (xanh)!").color(NamedTextColor.RED));
                    return true;
                }

                gameManager.placeBet(player, side, amount);
                break;

            default:
                player.sendMessage(Component.text("Lệnh không tồn tại.").color(NamedTextColor.RED));
        }
        return true;
    }
}