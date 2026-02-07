package com.example.cockfight;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import net.milkbowl.vault.economy.Economy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class GameManager {
    private final CockfightPlugin plugin;
    private final Map<Location, BlockData> backupBlocks = new HashMap<>();
    private Location arenaCenter;
    private Chicken chicken1; // Red
    private Chicken chicken2; // Blue
    private boolean isRunning = false;
    private boolean canBet = false; // Chỉ cho cược khi chưa đánh
    private BukkitRunnable skillTask;
    private final Random random = new Random();

    // --- HỆ THỐNG CƯỢC ---
    private final Map<UUID, Double> redBets = new HashMap<>();
    private final Map<UUID, Double> blueBets = new HashMap<>();

    public GameManager(CockfightPlugin plugin) {
        this.plugin = plugin;
    }

    // --- 1. BUILD ARENA ---
    public void buildArena(Location center) {
        if (!backupBlocks.isEmpty()) {
            center.getWorld().sendMessage(Component.text("Sân đấu đang hoạt động! /cockfight restart để làm mới.").color(NamedTextColor.RED));
            return;
        }
        this.arenaCenter = center;
        int radius = 4;
        int height = 5;
        World world = center.getWorld();
        int cx = center.getBlockX(); int cy = center.getBlockY(); int cz = center.getBlockZ();

        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                saveAndSetBlock(world.getBlockAt(x, cy - 1, z), Material.POLISHED_ANDESITE);
                for (int y = 0; y <= height; y++) {
                    Block block = world.getBlockAt(x, cy + y, z);
                    if (x == cx - radius || x == cx + radius || z == cz - radius || z == cz + radius) {
                        saveAndSetBlock(block, Material.OAK_FENCE);
                    } else if (y == height) {
                        saveAndSetBlock(block, Material.BARRIER);
                    } else {
                        saveAndSetBlock(block, Material.AIR);
                    }
                }
            }
        }
        spawnFighters(center);

        // Mở cổng đặt cược
        canBet = true;
        redBets.clear();
        blueBets.clear();

        Bukkit.broadcast(Component.text("========================================").color(NamedTextColor.GOLD));
        Bukkit.broadcast(Component.text("SÀN ĐẤU ĐÃ MỞ! ĐẶT CƯỢC NGAY!").color(NamedTextColor.GREEN));
        Bukkit.broadcast(Component.text("Gõ: /cockfight bet red <tiền>").color(NamedTextColor.RED));
        Bukkit.broadcast(Component.text("Gõ: /cockfight bet blue <tiền>").color(NamedTextColor.BLUE));
        Bukkit.broadcast(Component.text("========================================").color(NamedTextColor.GOLD));
    }

    private void saveAndSetBlock(Block block, Material newMaterial) {
        if (!backupBlocks.containsKey(block.getLocation())) backupBlocks.put(block.getLocation(), block.getBlockData());
        block.setType(newMaterial);
    }

    private void spawnFighters(Location center) {
        World world = center.getWorld();
        chicken1 = createChicken(center.clone().add(-2, 0, 0), "Chiến Kê Đỏ (RED)", NamedTextColor.RED);
        chicken2 = createChicken(center.clone().add(2, 0, 0), "Chiến Kê Xanh (BLUE)", NamedTextColor.BLUE);
    }

    private Chicken createChicken(Location loc, String name, NamedTextColor color) {
        Chicken c = (Chicken) loc.getWorld().spawnEntity(loc, EntityType.CHICKEN);
        c.customName(Component.text(name).color(color));
        c.setCustomNameVisible(true);
        c.setAI(false);
        c.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 4));
        return c;
    }

    // --- 2. BETTING SYSTEM (LOGIC MỚI) ---
    public boolean placeBet(Player player, String side, double amount) {
        if (!canBet) {
            player.sendMessage(Component.text("Đã khóa sổ! Không thể đặt cược lúc này.").color(NamedTextColor.RED));
            return false;
        }

        Economy eco = CockfightPlugin.getEconomy();
        if (!eco.has(player, amount)) {
            player.sendMessage(Component.text("Bạn không đủ tiền!").color(NamedTextColor.RED));
            return false;
        }

        // Kiểm tra xem đã cược 2 mang chưa (Chống gian lận)
        if ((side.equalsIgnoreCase("red") && blueBets.containsKey(player.getUniqueId())) ||
                (side.equalsIgnoreCase("blue") && redBets.containsKey(player.getUniqueId()))) {
            player.sendMessage(Component.text("Bạn chỉ được chọn 1 phe thôi!").color(NamedTextColor.RED));
            return false;
        }

        // Trừ tiền
        eco.withdrawPlayer(player, amount);

        if (side.equalsIgnoreCase("red")) {
            redBets.put(player.getUniqueId(), redBets.getOrDefault(player.getUniqueId(), 0.0) + amount);
            player.sendMessage(Component.text("Đã đặt " + amount + "$ cho Đội Đỏ!").color(NamedTextColor.RED));
        } else {
            blueBets.put(player.getUniqueId(), blueBets.getOrDefault(player.getUniqueId(), 0.0) + amount);
            player.sendMessage(Component.text("Đã đặt " + amount + "$ cho Đội Xanh!").color(NamedTextColor.BLUE));
        }
        return true;
    }

    // --- 3. START FIGHT ---
    public void startFight() {
        if (chicken1 == null || chicken2 == null || chicken1.isDead() || chicken2.isDead()) {
            if (arenaCenter != null && (chicken1 == null || chicken1.isDead())) spawnFighters(arenaCenter);
            else return;
        }

        isRunning = true;
        canBet = false; // Khóa cược

        Bukkit.broadcast(Component.text("KHÓA SỔ CƯỢC! TRẬN ĐẤU BẮT ĐẦU!").color(NamedTextColor.GOLD));

        setupWarrior(chicken1, chicken2);
        setupWarrior(chicken2, chicken1);
        startSkillScheduler();
    }

    public void restartMatch() {
        if (backupBlocks.isEmpty() || arenaCenter == null) return;
        isRunning = false;
        if (skillTask != null) skillTask.cancel();
        if (chicken1 != null) chicken1.remove();
        if (chicken2 != null) chicken2.remove();

        // Reset cược khi restart
        redBets.clear();
        blueBets.clear();
        canBet = true;

        spawnFighters(arenaCenter);
        Bukkit.broadcast(Component.text("Đã khởi động lại. Mời đặt cược lại!").color(NamedTextColor.GREEN));
    }

    private void setupWarrior(Chicken self, Chicken target) {
        self.setAI(true);
        self.removePotionEffect(PotionEffectType.RESISTANCE);
        double maxHealth = plugin.getConfig().getDouble("chicken-health", 100.0);
        var hpAttr = self.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) hpAttr.setBaseValue(maxHealth);
        self.setHealth(maxHealth);
        Bukkit.getMobGoals().removeGoal(self, VanillaGoal.PANIC);
        Bukkit.getMobGoals().addGoal(self, 1, new CockfightAttackGoal(self, plugin));
        self.setTarget(target);
    }

    // --- 4. SKILL SYSTEM ---
    private void startSkillScheduler() {
        skillTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isRunning || chicken1.isDead() || chicken2.isDead()) {
                    this.cancel();
                    return;
                }
                tryCastSkill(chicken1, chicken2);
                tryCastSkill(chicken2, chicken1);
            }
        };
        skillTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void tryCastSkill(Chicken caster, LivingEntity target) {
        if (random.nextDouble() > plugin.getConfig().getDouble("skill-chance", 0.5)) return;
        int skillType = random.nextInt(4);
        World world = caster.getWorld();
        switch (skillType) {
            case 0:
                world.playSound(caster.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 2f);
                world.spawnParticle(Particle.CRIT, target.getLocation().add(0, 0.5, 0), 10);
                target.damage(3.0, caster);
                sendMessage(caster, " tung Lôi Điểu!");
                break;
            case 1:
                world.playSound(caster.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 2f);
                world.spawnParticle(Particle.CLOUD, target.getLocation(), 10, 0.2, 0.2, 0.2, 0.1);
                target.setVelocity(target.getLocation().toVector().subtract(caster.getLocation().toVector()).normalize().multiply(1.0).setY(0.8));
                target.damage(4.0, caster);
                sendMessage(caster, " tung Cú Đá Xoáy!");
                break;
            case 2:
                world.playSound(caster.getLocation(), Sound.ENTITY_EGG_THROW, 1f, 0.5f);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, target.getLocation(), 1);
                world.createExplosion(target.getLocation(), 0F, false);
                target.damage(5.0, caster);
                sendMessage(caster, " ném Bom Trứng!");
                break;
            case 3:
                world.playSound(caster.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.5f);
                world.spawnParticle(Particle.SWEEP_ATTACK, caster.getLocation().add(0, 0.5, 0), 1);
                target.damage(6.0, caster);
                target.setVelocity(caster.getLocation().getDirection().multiply(1.5));
                sendMessage(caster, " dùng Tiếng Gáy Sonic!");
                break;
        }
    }

    private void sendMessage(Chicken c, String action) {
        c.getWorld().sendMessage(c.customName().append(Component.text(action).color(NamedTextColor.YELLOW)));
    }

    // --- 5. END & PAYOUT (TRẢ THƯỞNG) ---
    public void endFight() {
        isRunning = false; canBet = false;
        if (skillTask != null) skillTask.cancel();
        if (chicken1 != null) chicken1.remove();
        if (chicken2 != null) chicken2.remove();
        chicken1 = null; chicken2 = null;

        for (Map.Entry<Location, BlockData> entry : backupBlocks.entrySet()) {
            entry.getKey().getBlock().setBlockData(entry.getValue());
        }
        backupBlocks.clear(); arenaCenter = null;
        redBets.clear(); blueBets.clear();
        Bukkit.broadcast(Component.text("Sân đấu đã đóng.").color(NamedTextColor.GREEN));
    }

    public void forceEnd() { endFight(); }
    public boolean isGameRunning() { return isRunning; }

    public void onChickenDeath(Entity deadChicken) {
        if (!isRunning) return;

        // Xác định người thắng
        String winnerSide = "";
        Chicken winnerChicken = null;

        if (deadChicken.equals(chicken1)) { // Đỏ chết -> Xanh thắng
            winnerSide = "blue";
            winnerChicken = chicken2;
        } else if (deadChicken.equals(chicken2)) { // Xanh chết -> Đỏ thắng
            winnerSide = "red";
            winnerChicken = chicken1;
        }

        if (winnerChicken != null) {
            announceWinner(winnerChicken);
            processPayout(winnerSide); // Chia tiền
        }
    }

    private void processPayout(String winnerSide) {
        Economy eco = CockfightPlugin.getEconomy();

        // Tính tổng quỹ
        double totalRed = redBets.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalBlue = blueBets.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalPool = totalRed + totalBlue;

        Map<UUID, Double> winners = winnerSide.equals("red") ? redBets : blueBets;
        double totalWinningBets = winnerSide.equals("red") ? totalRed : totalBlue;

        if (winners.isEmpty()) {
            Bukkit.broadcast(Component.text("Không ai đặt cược cho bên thắng cả! Nhà cái ăn hết.").color(NamedTextColor.GRAY));
            return;
        }

        Bukkit.broadcast(Component.text("--- TRẢ THƯỞNG ---").color(NamedTextColor.GOLD));

        for (Map.Entry<UUID, Double> entry : winners.entrySet()) {
            UUID uid = entry.getKey();
            double betAmount = entry.getValue();

            // Công thức: Tiền nhận = (Tiền mình cược / Tổng tiền bên thắng) * Tổng quỹ
            // Nếu chỉ có 1 bên đặt, thì nhận lại đúng số tiền mình đặt (Hòa vốn)
            double payout = 0;
            if (totalWinningBets > 0) {
                payout = (betAmount / totalWinningBets) * totalPool;
            }

            OfflinePlayer p = Bukkit.getOfflinePlayer(uid);
            eco.depositPlayer(p, payout);

            if (p.isOnline() && p.getPlayer() != null) {
                p.getPlayer().sendMessage(Component.text("Bạn đã thắng cược! Nhận được: " + Math.round(payout) + "$").color(NamedTextColor.GREEN));
            }
        }
    }

    private void announceWinner(Chicken winner) {
        isRunning = false; canBet = false;
        if (skillTask != null) skillTask.cancel();

        Bukkit.broadcast(Component.text("==========================").color(NamedTextColor.GOLD));
        Bukkit.broadcast(winner.customName().append(Component.text(" CHIẾN THẮNG!").color(NamedTextColor.GOLD)));
        Bukkit.broadcast(Component.text("==========================").color(NamedTextColor.GOLD));

        winner.getWorld().spawnParticle(Particle.FIREWORK, winner.getLocation(), 50, 0.5, 0.5, 0.5, 0.1);
        winner.getWorld().playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    // AI Class (Giữ nguyên)
    public static class CockfightAttackGoal implements Goal<Chicken> {
        private final Chicken chicken;
        private final CockfightPlugin plugin;
        private final GoalKey<Chicken> key;
        private long lastAttackTime = 0;

        public CockfightAttackGoal(Chicken chicken, CockfightPlugin plugin) {
            this.chicken = chicken;
            this.plugin = plugin;
            this.key = GoalKey.of(Chicken.class, new NamespacedKey("cockfight", "attack"));
        }
        @Override
        public boolean shouldActivate() { return chicken.getTarget() != null && !chicken.getTarget().isDead(); }
        @Override
        public void tick() {
            LivingEntity target = chicken.getTarget();
            if (target == null) return;
            chicken.getPathfinder().moveTo(target, 1.4);
            if (chicken.getLocation().distanceSquared(target.getLocation()) < 2.5) {
                if (System.currentTimeMillis() - lastAttackTime > 800) {
                    chicken.swingMainHand();
                    target.damage(plugin.getConfig().getDouble("base-damage", 4.0), chicken);
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