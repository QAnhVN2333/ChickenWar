package com.example.chickenwar;

import com.example.chickenwar.config.ConfigKeys;
import com.example.chickenwar.managers.BetManager;
import com.example.chickenwar.managers.ConfigManager;
import com.example.chickenwar.managers.GameManager;
import com.example.chickenwar.utils.MessageUtils;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class ChickenWarCommand implements CommandExecutor {

    private final ChickenWarPlugin plugin;
    private final GameManager gameManager;

    public ChickenWarCommand(ChickenWarPlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("command.only-player"));
            return true;
        }

        if (args.length == 0) {
            sendHelpMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "help" -> sendHelpMenu(player);
            case "bet" -> handleBet(player, args);
            case "build" -> {
                if (!requireAdmin(player)) {
                    return true;
                }
                gameManager.buildAndInvite(player);
            }
            case "start" -> {
                if (!requireAdmin(player)) {
                    return true;
                }
                gameManager.startFight(player);
            }
            case "restart" -> {
                if (!requireAdmin(player)) {
                    return true;
                }
                gameManager.restartMatch(player);
            }
            case "end" -> {
                if (!requireAdmin(player)) {
                    return true;
                }
                gameManager.endFight(player);
            }
            case "bossbar" -> toggleBossBar(player);
            case "claim" -> claimPayout(player);
            case "jackpot" -> handleJackpot(player, args);
            case "setwarp" -> {
                if (!requireAdmin(player)) {
                    return true;
                }
                setWarp(player);
            }
            case "reload" -> {
                if (!requireAdmin(player)) {
                    return true;
                }
                plugin.getConfigManager().reload();
                MessageUtils.send(player, msg("command.reload-success"), NamedTextColor.GREEN);
            }
            default -> MessageUtils.send(player, msg("command.unknown"), NamedTextColor.RED);
        }

        return true;
    }

    private void sendHelpMenu(Player player) {
        MessageUtils.send(player, msg("help.title"), NamedTextColor.GOLD);
        MessageUtils.send(player, msg("help.player-1"), NamedTextColor.YELLOW);
        MessageUtils.send(player, msg("help.player-2"), NamedTextColor.YELLOW);
        MessageUtils.send(player, msg("help.player-3"), NamedTextColor.YELLOW);
        MessageUtils.send(player, msg("help.player-4"), NamedTextColor.YELLOW);
        MessageUtils.send(player, msg("help.player-5"), NamedTextColor.YELLOW);

        if (player.hasPermission("chickenwar.admin")) {
            MessageUtils.send(player, msg("help.admin-title"), NamedTextColor.RED);
            MessageUtils.send(player, msg("help.admin-1"), NamedTextColor.AQUA);
            MessageUtils.send(player, msg("help.admin-2"), NamedTextColor.AQUA);
            MessageUtils.send(player, msg("help.admin-3"), NamedTextColor.AQUA);
            MessageUtils.send(player, msg("help.admin-4"), NamedTextColor.LIGHT_PURPLE);
            MessageUtils.send(player, msg("help.admin-5"), NamedTextColor.LIGHT_PURPLE);
            MessageUtils.send(player, msg("help.admin-6"), NamedTextColor.LIGHT_PURPLE);
        } else {
            MessageUtils.send(player, msg("help.note-title"), NamedTextColor.GRAY);
            MessageUtils.send(player, msg("help.note-content"), NamedTextColor.GRAY);
        }

        MessageUtils.send(player, msg("help.footer"), NamedTextColor.GOLD);
    }

    private void handleBet(Player player, String[] args) {
        if (args.length < 3) {
            MessageUtils.send(player, msg("command.bet-usage"), NamedTextColor.RED);
            return;
        }

        try {
            double amount = Double.parseDouble(args[2]);
            if (amount <= 0) {
                throw new NumberFormatException();
            }
            gameManager.placeBet(player, args[1].toLowerCase(), amount);
        } catch (NumberFormatException e) {
            MessageUtils.send(player, msg("command.invalid-amount"), NamedTextColor.RED);
        }
    }

    private void handleJackpot(Player player, String[] args) {
        BetManager betManager = gameManager.getBetManager();

        if (args.length == 1) {
            sendJackpotInfo(player, betManager);
            return;
        }

        if (!requireAdmin(player)) {
            return;
        }

        if (args.length != 3) {
            MessageUtils.send(player, msg("command.jackpot-usage"), NamedTextColor.RED);
            return;
        }

        String action = args[1].toLowerCase();
        Long amount = parseLongAmount(args[2]);
        if (amount == null) {
            MessageUtils.send(player, msg("command.invalid-amount"), NamedTextColor.RED);
            return;
        }

        boolean success;
        switch (action) {
            case "add" -> success = betManager.addJackpot(amount);
            case "remove" -> success = betManager.removeJackpot(amount);
            case "set" -> success = betManager.setJackpot(amount);
            default -> {
                MessageUtils.send(player, msg("command.jackpot-usage"), NamedTextColor.RED);
                return;
            }
        }

        if (!success) {
            MessageUtils.send(player, msg("command.jackpot-invalid-operation", Map.of(
                    "amount", String.valueOf(amount),
                    "jackpot", String.valueOf(betManager.getJackpot())
            )), NamedTextColor.RED);
            return;
        }

        MessageUtils.send(player, msg("command.jackpot-updated", Map.of(
                "action", action,
                "amount", String.valueOf(amount),
                "jackpot", String.valueOf(betManager.getJackpot())
        )), NamedTextColor.GREEN);
    }

    private void sendJackpotInfo(Player player, BetManager betManager) {
        MessageUtils.send(player, msg("command.jackpot-info", Map.of(
                "jackpot", String.valueOf(betManager.getJackpot()),
                "round_seed", String.valueOf(betManager.getCurrentRoundServerSeed()),
                "seed_red", String.valueOf(betManager.getLastSeedRed()),
                "seed_blue", String.valueOf(betManager.getLastSeedBlue())
        )), NamedTextColor.GOLD);
    }

    private Long parseLongAmount(String raw) {
        try {
            long parsed = Long.parseLong(raw);
            if (parsed < 0L) {
                return null;
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void toggleBossBar(Player player) {
        boolean enabled = gameManager.toggleBossBar(player);
        if (enabled) {
            MessageUtils.send(player, msg("command.bossbar-enabled"), NamedTextColor.GREEN);
        } else {
            MessageUtils.send(player, msg("command.bossbar-disabled"), NamedTextColor.YELLOW);
        }
    }

    private void setWarp(Player player) {
        Location loc = player.getLocation();
        plugin.getConfig().set(ConfigKeys.Warp.WORLD, loc.getWorld().getName());
        plugin.getConfig().set(ConfigKeys.Warp.X, loc.getX());
        plugin.getConfig().set(ConfigKeys.Warp.Y, loc.getY());
        plugin.getConfig().set(ConfigKeys.Warp.Z, loc.getZ());
        plugin.getConfig().set(ConfigKeys.Warp.YAW, loc.getYaw());
        plugin.getConfig().set(ConfigKeys.Warp.PITCH, loc.getPitch());
        plugin.saveConfig();

        MessageUtils.send(player, msg("command.setwarp-success"), NamedTextColor.GREEN);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
    }

    private void claimPayout(Player player) {
        long claimed = gameManager.getBetManager().claimPending(player);
        if (claimed <= 0) {
            MessageUtils.send(player, msg("command.claim-empty"), NamedTextColor.YELLOW);
            return;
        }

        MessageUtils.send(player, msg("command.claim-success", java.util.Map.of("amount", String.valueOf(claimed))), NamedTextColor.GREEN);
    }

    private boolean requireAdmin(Player player) {
        if (player.hasPermission("chickenwar.admin")) {
            return true;
        }
        MessageUtils.send(player, msg("command.no-admin-permission"), NamedTextColor.RED);
        return false;
    }

    private String msg(String path) {
        ConfigManager configManager = plugin.getConfigManager();
        if (configManager == null) {
            return path;
        }
        return configManager.getMessage(path);
    }

    private String msg(String path, java.util.Map<String, String> placeholders) {
        ConfigManager configManager = plugin.getConfigManager();
        if (configManager == null) {
            return path;
        }
        return configManager.getMessage(path, placeholders);
    }
}

