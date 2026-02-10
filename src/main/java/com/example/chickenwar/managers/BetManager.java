package com.example.chickenwar.managers;

import com.example.chickenwar.ChickenWarPlugin;
import com.example.chickenwar.utils.MessageUtils;
import net.milkbowl.vault.economy.Economy;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BetManager {
    private final Map<UUID, Double> redBets = new HashMap<>();
    private final Map<UUID, Double> blueBets = new HashMap<>();

    // Mặc định là FALSE (Đóng) -> Chưa có sàn thì không cược được
    private boolean bettingOpen = false;

    public void openBetting() {
        redBets.clear();
        blueBets.clear();
        bettingOpen = true; // Mở cửa
    }

    public void closeBetting() {
        bettingOpen = false; // Khóa cửa
    }

    public boolean isBettingOpen() { return bettingOpen; }

    public boolean placeBet(Player player, String side, double amount) {
        // 1. KIỂM TRA TRẠNG THÁI CƯỢC
        if (!bettingOpen) {
            MessageUtils.send(player, "⛔ Cổng cược đang ĐÓNG! (Chưa có sàn đấu hoặc trận đã bắt đầu/kết thúc)", NamedTextColor.RED);
            return false;
        }

        Economy eco = ChickenWarPlugin.getEconomy();
        if (eco == null) {
            MessageUtils.send(player, "Lỗi hệ thống tiền tệ!", NamedTextColor.RED);
            return false;
        }

        if (!eco.has(player, amount)) {
            MessageUtils.send(player, "Không đủ tiền!", NamedTextColor.RED);
            return false;
        }

        // Kiểm tra xem đã cược bên kia chưa
        if ((side.equals("red") && blueBets.containsKey(player.getUniqueId())) ||
                (side.equals("blue") && redBets.containsKey(player.getUniqueId()))) {
            MessageUtils.send(player, "Bạn chỉ được chọn một phe!", NamedTextColor.RED);
            return false;
        }

        eco.withdrawPlayer(player, amount);

        String sideName;
        NamedTextColor sideColor;

        if (side.equals("red")) {
            redBets.put(player.getUniqueId(), redBets.getOrDefault(player.getUniqueId(), 0.0) + amount);
            sideName = "ĐỎ";
            sideColor = NamedTextColor.RED;
        } else {
            blueBets.put(player.getUniqueId(), blueBets.getOrDefault(player.getUniqueId(), 0.0) + amount);
            sideName = "XANH";
            sideColor = NamedTextColor.BLUE;
        }

        double totalRed = getTotalRed();
        double totalBlue = getTotalBlue();

        MessageUtils.broadcast("➤ " + player.getName() +
                " đã cược §a" + Math.round(amount) + "$§f vào §" + (side.equals("red") ? "c" : "9") + sideName +
                " §7(Tỉ lệ: §c" + Math.round(totalRed) + " §7vs §9" + Math.round(totalBlue) + "§7)", NamedTextColor.YELLOW);

        return true;
    }

    public void refundAll() {
        closeBetting(); // Đảm bảo đóng cửa khi hoàn tiền

        Economy eco = ChickenWarPlugin.getEconomy();
        if (eco == null) return;

        boolean refunded = false;
        for (var entry : redBets.entrySet()) {
            eco.depositPlayer(Bukkit.getOfflinePlayer(entry.getKey()), entry.getValue());
            refunded = true;
        }
        for (var entry : blueBets.entrySet()) {
            eco.depositPlayer(Bukkit.getOfflinePlayer(entry.getKey()), entry.getValue());
            refunded = true;
        }
        redBets.clear();
        blueBets.clear();

        if (refunded) MessageUtils.broadcast("⚠ Đã hoàn tiền cược cho tất cả mọi người.", NamedTextColor.RED);
    }

    public void processPayout(String winnerSide) {
        closeBetting(); // Đảm bảo đóng cửa khi trả thưởng

        Economy eco = ChickenWarPlugin.getEconomy();
        if (eco == null) return;

        double totalRed = getTotalRed();
        double totalBlue = getTotalBlue();
        double totalPool = totalRed + totalBlue;

        Map<UUID, Double> winners = winnerSide.equals("red") ? redBets : blueBets;
        Map<UUID, Double> losers = winnerSide.equals("red") ? blueBets : redBets;
        double totalWinningBets = winnerSide.equals("red") ? totalRed : totalBlue;

        if (winners.isEmpty()) {
            MessageUtils.broadcast("Nhà cái thắng toàn bộ (Không ai đặt bên thắng)!", NamedTextColor.GRAY);
        } else {
            MessageUtils.broadcast("--- TRẢ THƯỞNG (Phí 5%) ---", NamedTextColor.GOLD);
            for (Map.Entry<UUID, Double> entry : winners.entrySet()) {
                double payout = 0;
                if (totalWinningBets > 0) {
                    payout = (entry.getValue() / totalWinningBets) * totalPool;
                }
                double finalPayout = payout * 0.95;
                eco.depositPlayer(Bukkit.getOfflinePlayer(entry.getKey()), finalPayout);

                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null) MessageUtils.send(p, "Chúc mừng! Bạn nhận được: " + Math.round(finalPayout) + "$", NamedTextColor.GREEN);
            }
        }

        for (UUID uid : losers.keySet()) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) MessageUtils.send(p, "Rất tiếc! Bạn đã thua cược.", NamedTextColor.GRAY);
        }

        redBets.clear();
        blueBets.clear();
    }

    public double getTotalRed() { return redBets.values().stream().mapToDouble(d->d).sum(); }
    public double getTotalBlue() { return blueBets.values().stream().mapToDouble(d->d).sum(); }
}