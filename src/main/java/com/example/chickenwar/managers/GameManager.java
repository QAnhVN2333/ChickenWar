package com.example.chickenwar.managers;

import com.destroystokyo.paper.entity.ai.VanillaGoal;
import com.example.chickenwar.ChickenWarPlugin;
import com.example.chickenwar.utils.MessageUtils;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import java.util.EnumSet;

public class GameManager {
    private final ChickenWarPlugin plugin;
    private final ArenaManager arenaManager;
    private final BetManager betManager;
    private final SkillManager skillManager;

    private boolean isRunning = false;
    private BukkitRunnable gameTask;

    public GameManager(ChickenWarPlugin plugin) {
        this.plugin = plugin;
        this.arenaManager = new ArenaManager();
        this.betManager = new BetManager();
        this.skillManager = new SkillManager(plugin);
    }

    // 1. XÂY SÂN -> MỞ CƯỢC
    public void buildAndInvite(Player builder) {
        // ... (Logic check World/Warp giữ nguyên như cũ) ...
        String worldName = plugin.getConfig().getString("warp.world");
        if (worldName == null || !builder.getWorld().getName().equals(worldName)) {
            MessageUtils.send(builder, "Sai thế giới hoặc chưa set warp!", NamedTextColor.RED);
            return;
        }

        org.bukkit.World world = plugin.getServer().getWorld(worldName);
        double x = plugin.getConfig().getDouble("warp.x");
        double y = plugin.getConfig().getDouble("warp.y");
        double z = plugin.getConfig().getDouble("warp.z");
        Location warpLoc = new Location(world, x, y, z);

        if (arenaManager.hasArena()) {
            MessageUtils.send(builder, "Sân đang hoạt động!", NamedTextColor.RED);
            return;
        }

        arenaManager.buildArena(warpLoc, builder.getUniqueId());

        // MỞ CỔNG CƯỢC TẠI ĐÂY
        betManager.openBetting();

        MessageUtils.broadcast("========================================", NamedTextColor.GOLD);
        MessageUtils.broadcast("📢 ĐẠI CHIẾN GÀ ĐƯỢC TỔ CHỨC BỞI: " + builder.getName(), NamedTextColor.GREEN);
        MessageUtils.broadcast("➤ CỔNG CƯỢC ĐÃ MỞ! /cw bet <red/blue> <tiền>", NamedTextColor.YELLOW);
        MessageUtils.broadcast("========================================", NamedTextColor.GOLD);
    }

    // 2. BẮT ĐẦU -> KHÓA CƯỢC
    public void startFight(Player sender) {
        if (!checkPermission(sender)) return;
        if (!arenaManager.hasArena()) {
            MessageUtils.send(sender, "Chưa có sân đấu!", NamedTextColor.RED);
            return;
        }
        if (isRunning) {
            MessageUtils.send(sender, "Đang chạy!", NamedTextColor.RED);
            return;
        }
        if (checkChickensDead()) {
            MessageUtils.send(sender, "Gà chết rồi. /cw restart", NamedTextColor.RED);
            return;
        }

        isRunning = true;

        // KHÓA CỔNG CƯỢC TẠI ĐÂY
        betManager.closeBetting();

        MessageUtils.broadcast("=== ⛔ ĐÃ KHÓA SỔ CƯỢC - TRẬN ĐẤU BẮT ĐẦU ===", NamedTextColor.GOLD);
        MessageUtils.broadcast("Quỹ Đỏ: " + Math.round(betManager.getTotalRed()) + "$", NamedTextColor.RED);
        MessageUtils.broadcast("Quỹ Xanh: " + Math.round(betManager.getTotalBlue()) + "$", NamedTextColor.BLUE);

        setupChicken(arenaManager.getChicken1(), arenaManager.getChicken2());
        setupChicken(arenaManager.getChicken2(), arenaManager.getChicken1());

        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isRunning || checkChickensDead()) { this.cancel(); return; }

                skillManager.tryCastSkill(arenaManager.getChicken1(), arenaManager.getChicken2());
                if (checkChickensDead()) { this.cancel(); return; }
                skillManager.tryCastSkill(arenaManager.getChicken2(), arenaManager.getChicken1());
            }
        };
        gameTask.runTaskTimer(plugin, 20L, 20L);
    }

    // 3. KẾT THÚC (GÀ CHẾT) -> KHÓA CƯỢC (Chắc chắn)
    public void onChickenDeath(Entity deadChicken) {
        if (!isRunning) return;

        Chicken c1 = arenaManager.getChicken1();
        Chicken c2 = arenaManager.getChicken2();

        String winnerSide = null;
        Chicken winner = null;
        Chicken loser = null;

        if (deadChicken.getUniqueId().equals(c1.getUniqueId())) {
            winnerSide = "blue"; winner = c2; loser = c1;
        } else if (deadChicken.getUniqueId().equals(c2.getUniqueId())) {
            winnerSide = "red"; winner = c1; loser = c2;
        }

        if (winner != null) {
            stopGameLoop();

            // KHÓA CƯỢC NGAY LẬP TỨC
            betManager.closeBetting();

            cleanupWinner(winner);
            if (loser != null && !loser.isDead()) {
                loser.setHealth(0);
                loser.remove();
            }

            MessageUtils.broadcast("🏆 " + (winner.customName() != null ? ((net.kyori.adventure.text.TextComponent)winner.customName()).content() : "Chiến Kê") + " ĐÃ CHIẾN THẮNG!", NamedTextColor.GOLD);
            winner.getWorld().spawnParticle(Particle.FIREWORK, winner.getLocation(), 50, 0.5, 0.5, 0.5, 0.1);
            winner.getWorld().playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

            betManager.processPayout(winnerSide);
        }
    }

    // 4. HỦY SÂN / RESTART -> KHÓA CƯỢC
    public void forceEnd() {
        if (isRunning || betManager.isBettingOpen()) betManager.refundAll();

        stopGameLoop();
        betManager.closeBetting(); // Khóa

        arenaManager.clearArena();
        MessageUtils.broadcast("Sân đấu đã đóng cửa.", NamedTextColor.GREEN);
    }

    public void restartMatch(Player sender) {
        if (!checkPermission(sender)) return;
        if (!arenaManager.hasArena()) return;

        betManager.refundAll(); // Hoàn tiền và khóa
        stopGameLoop();

        // Chỉ khi restart thành công mới mở lại
        arenaManager.spawnFighters();
        betManager.openBetting();

        MessageUtils.broadcast("♻ Host đã khởi động lại trận đấu! Cổng cược ĐÃ MỞ.", NamedTextColor.GREEN);
    }

    // --- CÁC HÀM PHỤ TRỢ (Giữ nguyên) ---
    public void endFight(Player sender) { if (checkPermission(sender)) forceEnd(); }

    private boolean checkPermission(Player p) {
        if (p.hasPermission("chickenwar.admin")) return true;
        if (arenaManager.getHostUUID() != null && arenaManager.getHostUUID().equals(p.getUniqueId())) return true;
        MessageUtils.send(p, "Chỉ Chủ phòng (Host) mới được dùng lệnh này!", NamedTextColor.RED);
        return false;
    }

    private void stopGameLoop() { isRunning = false; if (gameTask != null) gameTask.cancel(); }

    private boolean checkChickensDead() {
        return arenaManager.getChicken1() == null || arenaManager.getChicken1().isDead() ||
                arenaManager.getChicken2() == null || arenaManager.getChicken2().isDead();
    }

    private void cleanupWinner(Chicken winner) {
        if (winner == null || winner.isDead()) return;
        winner.setInvulnerable(true);
        winner.setHealth(winner.getAttribute(Attribute.MAX_HEALTH).getValue());
        winner.clearActivePotionEffects();
        winner.setFireTicks(0);
        winner.setTarget(null);
        Bukkit.getMobGoals().removeAllGoals(winner);
    }

    private void setupChicken(Chicken c, LivingEntity target) {
        if (c == null) return;
        c.setAI(true);
        c.setInvulnerable(false);
        c.clearActivePotionEffects();
        double hp = plugin.getConfig().getDouble("chicken-health", 60.0);
        AttributeInstance attr = c.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) attr.setBaseValue(hp);
        c.setHealth(hp);
        Bukkit.getMobGoals().removeGoal(c, VanillaGoal.PANIC);
        Bukkit.getMobGoals().addGoal(c, 1, new ChickenWarAttackGoal(c, plugin));
        c.setTarget(target);
    }

    public BetManager getBetManager() { return betManager; }

    // AI Class giữ nguyên như cũ
    public static class ChickenWarAttackGoal implements Goal<Chicken> {
        // ... (Code AI giữ nguyên) ...
        private final Chicken chicken;
        private final ChickenWarPlugin plugin;
        private final GoalKey<Chicken> key;
        private long lastAttackTime = 0;
        public ChickenWarAttackGoal(Chicken chicken, ChickenWarPlugin plugin) {
            this.chicken = chicken; this.plugin = plugin; this.key = GoalKey.of(Chicken.class, new NamespacedKey("chickenwar", "attack"));
        }
        @Override public boolean shouldActivate() { return chicken.getTarget() != null && !chicken.getTarget().isDead(); }
        @Override public void tick() {
            LivingEntity target = chicken.getTarget();
            if (target == null) return;
            chicken.getPathfinder().moveTo(target, 1.4);
            if (chicken.getLocation().distanceSquared(target.getLocation()) < 2.5) {
                if (System.currentTimeMillis() - lastAttackTime > 800) {
                    chicken.swingMainHand();
                    target.damage(plugin.getConfig().getDouble("base-damage", 5.0), chicken);
                    lastAttackTime = System.currentTimeMillis();
                }
            }
        }
        @Override public @NotNull GoalKey<Chicken> getKey() { return key; }
        @Override public @NotNull EnumSet<GoalType> getTypes() { return EnumSet.of(GoalType.TARGET, GoalType.MOVE); }
    }
}