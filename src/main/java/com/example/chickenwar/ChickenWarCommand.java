package com.example.chickenwar;

import com.example.chickenwar.managers.GameManager;
import com.example.chickenwar.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
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
            sender.sendMessage("Lệnh này chỉ dành cho người chơi.");
            return true;
        }

        // 1. Lệnh /cw (Không tham số) -> Teleport đến Đấu Trường
        if (args.length == 0) {
            teleportToArena(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "help":
                sendHelpMenu(player);
                break;

            case "bet":
                handleBet(player, args);
                break;

            case "build":
                // Ai cũng build được (để làm Host), nhưng phải ở đúng world
                gameManager.buildAndInvite(player);
                break;

            // --- CÁC LỆNH QUẢN LÝ (HOST HOẶC ADMIN) ---
            case "start":
                gameManager.startFight(player);
                break;
            case "restart":
                gameManager.restartMatch(player);
                break;
            case "end":
                gameManager.endFight(player);
                break;

            // --- CÁC LỆNH ADMIN (OP) ---
            case "setwarp":
                if (!player.hasPermission("chickenwar.admin")) {
                    MessageUtils.send(player, "Bạn không có quyền Admin!", NamedTextColor.RED);
                    return true;
                }
                setWarp(player);
                break;

            case "reload":
                if (!player.hasPermission("chickenwar.admin")) {
                    MessageUtils.send(player, "Bạn không có quyền Admin!", NamedTextColor.RED);
                    return true;
                }
                plugin.reloadConfig();
                MessageUtils.send(player, "Đã tải lại cấu hình (Config Reloaded)!", NamedTextColor.GREEN);
                break;

            default:
                MessageUtils.send(player, "Lệnh không tồn tại. Gõ /cw help để xem hướng dẫn.", NamedTextColor.RED);
        }

        return true;
    }

    // --- LOGIC XỬ LÝ RIÊNG ---

    private void sendHelpMenu(Player player) {
        MessageUtils.send(player, "============== HƯỚNG DẪN CHICKEN WAR ==============", NamedTextColor.GOLD);

        // 1. Lệnh cho NGƯỜI CHƠI
        MessageUtils.send(player, "➤ /cw : Dịch chuyển ngay đến Đấu Trường.", NamedTextColor.YELLOW);
        MessageUtils.send(player, "➤ /cw bet <red/blue> <tiền> : Đặt cược cho Gà Đỏ hoặc Xanh.", NamedTextColor.YELLOW);
        MessageUtils.send(player, "➤ /cw build : Xây sân đấu (Bạn sẽ trở thành Host/Chủ phòng).", NamedTextColor.YELLOW);

        // 2. Lệnh cho ADMIN (Chỉ hiện nếu có quyền)
        if (player.hasPermission("chickenwar.admin")) {
            MessageUtils.send(player, "--- LỆNH ADMIN / HOST ---", NamedTextColor.RED);
            MessageUtils.send(player, "➤ /cw start : Bắt đầu trận đấu (Sau khi đã có người cược).", NamedTextColor.AQUA);
            MessageUtils.send(player, "➤ /cw end : Kết thúc trận đấu & Dọn sân.", NamedTextColor.AQUA);
            MessageUtils.send(player, "➤ /cw restart : Hủy trận cũ, bắt đầu trận mới.", NamedTextColor.AQUA);
            MessageUtils.send(player, "➤ /cw setwarp : Đặt điểm warp tại vị trí đang đứng.", NamedTextColor.LIGHT_PURPLE);
            MessageUtils.send(player, "➤ /cw reload : Tải lại file config.", NamedTextColor.LIGHT_PURPLE);
        } else {
            // Nhắc nhẹ người chơi về quyền Host
            MessageUtils.send(player, "--- LƯU Ý ---", NamedTextColor.GRAY);
            MessageUtils.send(player, "Khi bạn dùng lệnh /cw build, bạn sẽ có quyền dùng start/end/restart.", NamedTextColor.GRAY);
        }

        MessageUtils.send(player, "==================================================", NamedTextColor.GOLD);
    }

    private void handleBet(Player player, String[] args) {
        if (args.length < 3) {
            MessageUtils.send(player, "Sai cú pháp! Dùng: /cw bet <red/blue> <số tiền>", NamedTextColor.RED);
            return;
        }
        try {
            double amount = Double.parseDouble(args[2]);
            if (amount <= 0) throw new NumberFormatException();
            gameManager.getBetManager().placeBet(player, args[1].toLowerCase(), amount);
        } catch (NumberFormatException e) {
            MessageUtils.send(player, "Số tiền không hợp lệ!", NamedTextColor.RED);
        }
    }

    private void teleportToArena(Player player) {
        String worldName = plugin.getConfig().getString("warp.world");
        if (worldName == null) {
            MessageUtils.send(player, "Chưa thiết lập điểm Warp! Admin hãy dùng /cw setwarp.", NamedTextColor.RED);
            return;
        }

        org.bukkit.World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            MessageUtils.send(player, "Thế giới sự kiện không tồn tại hoặc chưa load!", NamedTextColor.RED);
            return;
        }

        double x = plugin.getConfig().getDouble("warp.x");
        double y = plugin.getConfig().getDouble("warp.y");
        double z = plugin.getConfig().getDouble("warp.z");
        float yaw = (float) plugin.getConfig().getDouble("warp.yaw");
        float pitch = (float) plugin.getConfig().getDouble("warp.pitch");

        player.teleport(new Location(world, x, y, z, yaw, pitch));
        MessageUtils.send(player, "Đã dịch chuyển đến Đấu Trường Gà!", NamedTextColor.GREEN);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
    }

    private void setWarp(Player player) {
        Location loc = player.getLocation();
        plugin.getConfig().set("warp.world", loc.getWorld().getName());
        plugin.getConfig().set("warp.x", loc.getX());
        plugin.getConfig().set("warp.y", loc.getY());
        plugin.getConfig().set("warp.z", loc.getZ());
        plugin.getConfig().set("warp.yaw", loc.getYaw());
        plugin.getConfig().set("warp.pitch", loc.getPitch());
        plugin.saveConfig();
        MessageUtils.send(player, "✅ Đã lưu tọa độ Warp sự kiện tại đây!", NamedTextColor.GREEN);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
    }
}