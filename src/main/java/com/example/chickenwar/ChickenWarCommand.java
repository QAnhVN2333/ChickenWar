package com.example.chickenwar;

import com.example.chickenwar.managers.GameManager;
import com.example.chickenwar.utils.MessageUtils;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

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
            sender.sendMessage("Lệnh này chỉ dành cho người chơi (Ingame)!");
            return true;
        }

        // 1. Nếu không nhập gì -> Hiện hướng dẫn theo quyền
        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        // 2. Xử lý các lệnh
        switch (sub) {
            // --- LỆNH NGƯỜI CHƠI ---
            case "bet":
                if (!player.hasPermission("chickenwar.bet")) {
                    MessageUtils.send(player, "Bạn không có quyền cá cược!", NamedTextColor.RED);
                    return true;
                }
                if (args.length < 3) {
                    MessageUtils.send(player, "Sai cú pháp! Dùng: /cw bet <red/blue> <tiền>", NamedTextColor.RED);
                    return true;
                }
                try {
                    double amount = Double.parseDouble(args[2]);
                    if (amount <= 0) throw new NumberFormatException();

                    gameManager.getBetManager().placeBet(player, args[1].toLowerCase(), amount);
                } catch (NumberFormatException e) {
                    MessageUtils.send(player, "Số tiền không hợp lệ!", NamedTextColor.RED);
                }
                break;

            // --- LỆNH ADMIN ---
            case "build":
                if (checkAdmin(player)) gameManager.buildAndInvite(player);
                break;
            case "start":
                if (checkAdmin(player)) gameManager.startFight(player);
                break;
            case "restart":
                if (checkAdmin(player)) gameManager.restartMatch();
                break;
            case "end":
                if (checkAdmin(player)) gameManager.endFight();
                break;
            case "reload":
                if (checkAdmin(player)) {
                    plugin.reloadConfig();
                    MessageUtils.send(player, "Đã reload config thành công!", NamedTextColor.GREEN);
                }
                break;

            // --- LỆNH SAI ---
            default:
                MessageUtils.send(player, "Lệnh không tồn tại!", NamedTextColor.RED);
                showHelp(player); // Hiện lại hướng dẫn
                break;
        }

        return true;
    }

    // --- HÀM KIỂM TRA QUYỀN ADMIN NHANH ---
    private boolean checkAdmin(Player player) {
        if (!player.hasPermission("chickenwar.admin")) {
            MessageUtils.send(player, "Bạn không có quyền Admin để dùng lệnh này.", NamedTextColor.RED);
            return false;
        }
        return true;
    }

    // --- HÀM HIỂN THỊ HƯỚNG DẪN THÔNG MINH ---
    private void showHelp(Player player) {
        MessageUtils.send(player, "============== HƯỚNG DẪN CHICKEN WAR ==============", NamedTextColor.GOLD);

        // Luôn hiện lệnh cá cược (vì ai cũng cần dùng)
        MessageUtils.send(player, "➤ /cw bet <red/blue> <tiền>: Đặt cược cho Đỏ hoặc Xanh", NamedTextColor.GREEN);

        // Chỉ hiện lệnh quản lý nếu là Admin
        if (player.hasPermission("chickenwar.admin")) {
            MessageUtils.send(player, "------------------ ADMIN ------------------", NamedTextColor.GRAY);
            MessageUtils.send(player, "➤ /cw build: Xây đấu trường & Mở cược", NamedTextColor.YELLOW);
            MessageUtils.send(player, "➤ /cw start: Bắt đầu trận đấu", NamedTextColor.YELLOW);
            MessageUtils.send(player, "➤ /cw restart: Khởi động lại trận mới", NamedTextColor.YELLOW);
            MessageUtils.send(player, "➤ /cw end: Dọn sân & Kết thúc", NamedTextColor.YELLOW);
            MessageUtils.send(player, "➤ /cw reload: Nạp lại file config", NamedTextColor.YELLOW);
        }
        MessageUtils.send(player, "===================================================", NamedTextColor.GOLD);
    }
}