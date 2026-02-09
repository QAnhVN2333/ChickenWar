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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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

    public void buildAndInvite(Player host) {
        if (arenaManager.hasArena()) {
            MessageUtils.send(host, "Sân đang hoạt động! Dùng /cw restart nếu muốn chơi lại.", NamedTextColor.RED);
            return;
        }

        arenaManager.buildArena(host.getLocation());
        betManager.openBetting();

        MessageUtils.broadcast("========================================", NamedTextColor.GOLD);
        MessageUtils.broadcast("📢 LOA LOA: " + host.getName() + " ĐÃ TỔ CHỨC ĐẠI CHIẾN GÀ!", NamedTextColor.GREEN);
        MessageUtils.broadcast("➤ Mời anh em tham gia đặt cược ngay!", NamedTextColor.YELLOW);
        MessageUtils.broadcast("➤ /cw bet red <tiền> (Đặt Đội Đỏ)", NamedTextColor.RED);
        MessageUtils.broadcast("➤ /cw bet blue <tiền> (Đặt Đội Xanh)", NamedTextColor.BLUE);
        MessageUtils.broadcast("========================================", NamedTextColor.GOLD);
    }

    // --- CẬP NHẬT LOGIC START FIGHT ---
    public void startFight(Player admin) {
        // 1. Kiểm tra có sân chưa
        if (!arenaManager.hasArena()) {
            MessageUtils.send(admin, "Chưa có sân đấu! Hãy dùng /cw build trước.", NamedTextColor.RED);
            return;
        }

        // 2. Kiểm tra trận đấu có đang chạy không
        if (isRunning) {
            MessageUtils.send(admin, "Trận đấu đang diễn ra rồi! Không thể start lại.", NamedTextColor.RED);
            return;
        }

        // 3. Kiểm tra gà có còn sống không (Trường hợp trận đấu đã xong nhưng Admin cố start lại)
        Chicken c1 = arenaManager.getChicken1();
        Chicken c2 = arenaManager.getChicken2();

        if (c1 == null || c1.isDead() || c2 == null || c2.isDead()) {
            MessageUtils.send(admin, "Gà đã chết hoặc kết thúc! Vui lòng dùng /cw restart để bắt đầu ván mới.", NamedTextColor.RED);
            return;
        }

        // --- BẮT ĐẦU ---
        isRunning = true;
        betManager.closeBetting();

        MessageUtils.broadcast("=== KHÓA SỔ CƯỢC - TRẬN ĐẤU BẮT ĐẦU ===", NamedTextColor.GOLD);
        MessageUtils.broadcast("Quỹ Đỏ: " + Math.round(betManager.getTotalRed()) + "$", NamedTextColor.RED);
        MessageUtils.broadcast("Quỹ Xanh: " + Math.round(betManager.getTotalBlue()) + "$", NamedTextColor.BLUE);

        setupChicken(c1, c2);
        setupChicken(c2, c1);

        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isRunning || checkChickensDead()) {
                    this.cancel();
                    return;
                }
                skillManager.tryCastSkill(arenaManager.getChicken1(), arenaManager.getChicken2());
                skillManager.tryCastSkill(arenaManager.getChicken2(), arenaManager.getChicken1());
            }
        };
        gameTask.runTaskTimer(plugin, 20L, 20L);
    }

    public void endFight() {
        if (isRunning || betManager.isBettingOpen()) {
            betManager.refundAll();
        }

        stopGameLoop();
        arenaManager.clearArena();
        MessageUtils.broadcast("Sân đấu đã đóng cửa.", NamedTextColor.GREEN);
    }

    public void restartMatch() {
        if (!arenaManager.hasArena()) return;

        betManager.refundAll();

        stopGameLoop();

        betManager.openBetting();
        arenaManager.spawnFighters();

        MessageUtils.broadcast("♻ Đã khởi động lại trận đấu! Mời đặt cược lại.", NamedTextColor.GREEN);
    }

    public void onChickenDeath(Entity deadChicken) {
        if (!isRunning) return;

        Chicken c1 = arenaManager.getChicken1();
        Chicken c2 = arenaManager.getChicken2();

        String winnerSide = null;
        Chicken winner = null;

        if (deadChicken.equals(c1)) {
            winnerSide = "blue"; winner = c2;
        } else if (deadChicken.equals(c2)) {
            winnerSide = "red"; winner = c1;
        }

        if (winner != null) {
            stopGameLoop();

            MessageUtils.broadcast("🏆 " + (winner.customName() != null ? ((net.kyori.adventure.text.TextComponent)winner.customName()).content() : "Chiến Kê") + " ĐÃ CHIẾN THẮNG!", NamedTextColor.GOLD);
            winner.getWorld().spawnParticle(Particle.FIREWORK, winner.getLocation(), 50, 0.5, 0.5, 0.5, 0.1);
            winner.getWorld().playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

            betManager.processPayout(winnerSide);
        }
    }

    private void stopGameLoop() {
        isRunning = false;
        if (gameTask != null && !gameTask.isCancelled()) {
            gameTask.cancel();
        }
    }

    private boolean checkChickensDead() {
        return arenaManager.getChicken1() == null || arenaManager.getChicken1().isDead() ||
                arenaManager.getChicken2() == null || arenaManager.getChicken2().isDead();
    }

    private void setupChicken(Chicken c, LivingEntity target) {
        if (c == null) return;

        c.setAI(true);
        for (PotionEffect effect : c.getActivePotionEffects()) {
            c.removePotionEffect(effect.getType());
        }

        double hp = plugin.getConfig().getDouble("chicken-health", 100.0);
        AttributeInstance attr = c.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) attr.setBaseValue(hp);
        c.setHealth(hp);

        Bukkit.getMobGoals().removeGoal(c, VanillaGoal.PANIC);
        Bukkit.getMobGoals().addGoal(c, 1, new ChickenWarAttackGoal(c, plugin));
        c.setTarget(target);
    }

    public BetManager getBetManager() {
        return betManager;
    }

    public void forceEnd() {
        endFight();
    }

    public static class ChickenWarAttackGoal implements Goal<Chicken> {
        private final Chicken chicken;
        private final ChickenWarPlugin plugin;
        private final GoalKey<Chicken> key;
        private long lastAttackTime = 0;

        public ChickenWarAttackGoal(Chicken chicken, ChickenWarPlugin plugin) {
            this.chicken = chicken;
            this.plugin = plugin;
            this.key = GoalKey.of(Chicken.class, new NamespacedKey("chickenwar", "attack"));
        }

        @Override
        public boolean shouldActivate() {
            return chicken.getTarget() != null && !chicken.getTarget().isDead();
        }

        @Override
        public void tick() {
            LivingEntity target = chicken.getTarget();
            if (target == null) return;

            chicken.getPathfinder().moveTo(target, 1.4);

            if (chicken.getLocation().distanceSquared(target.getLocation()) < 2.5) {
                if (System.currentTimeMillis() - lastAttackTime > 800) {
                    chicken.swingMainHand();
                    double dmg = plugin.getConfig().getDouble("base-damage", 5.0);
                    target.damage(dmg, chicken);
                    lastAttackTime = System.currentTimeMillis();
                }
            }
        }

        @Override
        public @NotNull GoalKey<Chicken> getKey() { return key; }

        @Override
        public @NotNull EnumSet<GoalType> getTypes() { return EnumSet.of(GoalType.TARGET, GoalType.MOVE); }
    }
}