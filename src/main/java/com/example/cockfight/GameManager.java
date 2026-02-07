package com.example.cockfight;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class GameManager {
    private final CockfightPlugin plugin;
    private final Map<Location, BlockData> backupBlocks = new HashMap<>();
    private Location arenaCenter; // Lưu vị trí tâm sân để Restart
    private Chicken chicken1;
    private Chicken chicken2;
    private boolean isRunning = false;
    private BukkitRunnable skillTask;
    private final Random random = new Random();

    public GameManager(CockfightPlugin plugin) {
        this.plugin = plugin;
    }

    // --- 1. BUILD ARENA (CÓ NẮP BARRIER) ---
    public void buildArena(Location center) {
        if (!backupBlocks.isEmpty()) {
            center.getWorld().sendMessage(Component.text("Sân đấu đã tồn tại! Dùng /cockfight restart để chơi lại.").color(NamedTextColor.RED));
            return;
        }

        this.arenaCenter = center; // Lưu lại vị trí
        int radius = 4;
        int height = 5; // Tăng chiều cao lên 5 (lớp trên cùng sẽ là nắp)

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        World world = center.getWorld();

        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                // Sàn đấu (y-1)
                saveAndSetBlock(world.getBlockAt(x, cy - 1, z), Material.POLISHED_ANDESITE);

                // Các lớp bên trên
                for (int y = 0; y <= height; y++) {
                    Block block = world.getBlockAt(x, cy + y, z);

                    // 1. Nếu là đường viền -> Hàng rào
                    if (x == cx - radius || x == cx + radius || z == cz - radius || z == cz + radius) {
                        saveAndSetBlock(block, Material.OAK_FENCE);
                    }
                    // 2. Nếu là lớp cao nhất -> Nắp Barrier (Để gà không bay ra)
                    else if (y == height) {
                        saveAndSetBlock(block, Material.BARRIER);
                    }
                    // 3. Còn lại -> Không khí (Xóa block cũ nếu có)
                    else {
                        saveAndSetBlock(block, Material.AIR);
                    }
                }
            }
        }
        spawnFighters(center);
        center.getWorld().sendMessage(Component.text("Đấu trường đã sẵn sàng! (Có mái che an toàn)").color(NamedTextColor.YELLOW));
    }

    private void saveAndSetBlock(Block block, Material newMaterial) {
        if (!backupBlocks.containsKey(block.getLocation())) {
            backupBlocks.put(block.getLocation(), block.getBlockData());
        }
        block.setType(newMaterial);
    }

    private void spawnFighters(Location center) {
        World world = center.getWorld();
        chicken1 = createChicken(center.clone().add(-2, 0, 0), "Chiến Kê Đỏ", NamedTextColor.RED);
        chicken2 = createChicken(center.clone().add(2, 0, 0), "Chiến Kê Xanh", NamedTextColor.BLUE);
    }

    private Chicken createChicken(Location loc, String name, NamedTextColor color) {
        Chicken c = (Chicken) loc.getWorld().spawnEntity(loc, EntityType.CHICKEN);
        c.customName(Component.text(name).color(color));
        c.setCustomNameVisible(true);
        c.setAI(false);
        c.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 4));
        return c;
    }

    // --- 2. START FIGHT ---
    public void startFight() {
        if (chicken1 == null || chicken2 == null || chicken1.isDead() || chicken2.isDead()) {
            // Nếu gà chết hoặc không có, thử spawn lại (cho trường hợp restart)
            if (arenaCenter != null && (chicken1 == null || chicken1.isDead())) {
                spawnFighters(arenaCenter);
            } else {
                return;
            }
        }

        isRunning = true;
        setupWarrior(chicken1, chicken2);
        setupWarrior(chicken2, chicken1);
        startSkillScheduler();

        chicken1.getWorld().sendMessage(Component.text("TRẬN ĐẤU BẮT ĐẦU!").color(NamedTextColor.RED));
        chicken1.getWorld().playSound(chicken1.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
    }

    // --- TÍNH NĂNG MỚI: RESTART ---
    public void restartMatch() {
        if (backupBlocks.isEmpty() || arenaCenter == null) {
            // Chưa có sân thì không restart được
            return;
        }

        // 1. Dừng game hiện tại
        isRunning = false;
        if (skillTask != null && !skillTask.isCancelled()) skillTask.cancel();

        // 2. Xóa gà cũ
        if (chicken1 != null && !chicken1.isDead()) chicken1.remove();
        if (chicken2 != null && !chicken2.isDead()) chicken2.remove();

        // 3. Spawn gà mới
        spawnFighters(arenaCenter);

        // 4. Bắt đầu luôn
        startFight();

        Bukkit.broadcast(Component.text("Trận đấu đã được khởi động lại!").color(NamedTextColor.GREEN));
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

    // --- 3. SKILL SYSTEM (AN TOÀN) ---
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
        double chance = plugin.getConfig().getDouble("skill-chance", 0.5);
        if (random.nextDouble() > chance) return;

        int skillType = random.nextInt(4);
        World world = caster.getWorld();

        switch (skillType) {
            case 0: // Lôi Điểu
                world.playSound(caster.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 2f);
                world.spawnParticle(Particle.CRIT, target.getLocation().add(0, 0.5, 0), 10);
                target.damage(3.0, caster);
                sendMessage(caster, " tung Lôi Điểu!");
                break;
            case 1: // Cú Đá Xoáy
                world.playSound(caster.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 2f);
                world.spawnParticle(Particle.CLOUD, target.getLocation(), 10, 0.2, 0.2, 0.2, 0.1);
                target.setVelocity(target.getLocation().toVector().subtract(caster.getLocation().toVector()).normalize().multiply(1.0).setY(0.8));
                target.damage(4.0, caster);
                sendMessage(caster, " tung Cú Đá Xoáy!");
                break;
            case 2: // Bom Trứng
                world.playSound(caster.getLocation(), Sound.ENTITY_EGG_THROW, 1f, 0.5f);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, target.getLocation(), 1);
                world.createExplosion(target.getLocation(), 0F, false);
                target.damage(5.0, caster);
                sendMessage(caster, " ném Bom Trứng!");
                break;
            case 3: // Tiếng Gáy Sonic
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

    // --- 4. END & UTILS ---
    public void endFight() {
        isRunning = false;
        if (skillTask != null && !skillTask.isCancelled()) skillTask.cancel();

        if (chicken1 != null && !chicken1.isDead()) chicken1.remove();
        if (chicken2 != null && !chicken2.isDead()) chicken2.remove();
        chicken1 = null; chicken2 = null;

        for (Map.Entry<Location, BlockData> entry : backupBlocks.entrySet()) {
            entry.getKey().getBlock().setBlockData(entry.getValue());
        }
        backupBlocks.clear();
        arenaCenter = null; // Xóa vị trí sân
        Bukkit.broadcast(Component.text("Sân đấu đã dọn dẹp.").color(NamedTextColor.GREEN));
    }

    public void forceEnd() { endFight(); }
    public boolean isGameRunning() { return isRunning; }

    public void onChickenDeath(Entity deadChicken) {
        if (!isRunning) return;
        if (deadChicken.equals(chicken1)) announceWinner(chicken2);
        else if (deadChicken.equals(chicken2)) announceWinner(chicken1);
    }

    private void announceWinner(Chicken winner) {
        isRunning = false;
        if (skillTask != null) skillTask.cancel();

        Bukkit.broadcast(Component.text("==========================").color(NamedTextColor.GOLD));
        Bukkit.broadcast(winner.customName().append(Component.text(" VÔ ĐỊCH THIÊN HẠ!").color(NamedTextColor.GOLD)));
        Bukkit.broadcast(Component.text("==========================").color(NamedTextColor.GOLD));

        winner.getWorld().spawnParticle(Particle.FIREWORK, winner.getLocation(), 50, 0.5, 0.5, 0.5, 0.1);
        winner.getWorld().playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    // --- INNER CLASS: AI ---
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
        public boolean shouldActivate() {
            return chicken.getTarget() != null && !chicken.getTarget().isDead();
        }

        @Override
        public void tick() {
            LivingEntity target = chicken.getTarget();
            if (target == null) return;

            chicken.getPathfinder().moveTo(target, 1.4);

            double dist = chicken.getLocation().distanceSquared(target.getLocation());
            if (dist < 2.5) {
                if (System.currentTimeMillis() - lastAttackTime > 800) {
                    chicken.swingMainHand();
                    double dmg = plugin.getConfig().getDouble("base-damage", 4.0);
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