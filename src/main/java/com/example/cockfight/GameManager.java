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
    private boolean canBet = false;
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

    // --- 2. BETTING SYSTEM ---
    public boolean placeBet(Player player, String side, double amount) {
        Economy eco = CockfightPlugin.getEconomy();
        if (eco == null) {
            player.sendMessage(Component.text("Server chưa cài đặt hệ thống tiền tệ!").color(NamedTextColor.RED));
            return false;
        }

        if (!canBet) {
            player.sendMessage(Component.text("Đã khóa sổ! Không thể đặt cược lúc này.").color(NamedTextColor.RED));
            return false;
        }

        if (!eco.has(player, amount)) {
            player.sendMessage(Component.text("Bạn không đủ tiền!").color(NamedTextColor.RED));
            return false;
        }

        if ((side.equalsIgnoreCase("red") && blueBets.containsKey(player.getUniqueId())) ||
                (side.equalsIgnoreCase("blue") && redBets.containsKey(player.getUniqueId()))) {
            player.sendMessage(Component.text("Bạn chỉ được chọn 1 phe thôi!").color(NamedTextColor.RED));
            return false;
        }

        eco.withdrawPlayer(player, amount);

        String betInfo;
        if (side.equalsIgnoreCase("red")) {
            redBets.put(player.getUniqueId(), redBets.getOrDefault(player.getUniqueId(), 0.0) + amount);
            betInfo = "§cĐỎ (RED)";
        } else {
            blueBets.put(player.getUniqueId(), blueBets.getOrDefault(player.getUniqueId(), 0.0) + amount);
            betInfo = "§9XANH (BLUE)";
        }

        double totalRed = redBets.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalBlue = blueBets.values().stream().mapToDouble(Double::doubleValue).sum();

        Bukkit.broadcast(Component.text("➤ ").color(NamedTextColor.GOLD)
                .append(player.displayName())
                .append(Component.text(" đã cược ").color(NamedTextColor.YELLOW))
                .append(Component.text(Math.round(amount) + "$").color(NamedTextColor.GREEN))
                .append(Component.text(" vào " + betInfo))
                .append(Component.text(" | Tỉ số tiền: §c" + Math.round(totalRed) + "$ §fvs §9" + Math.round(totalBlue) + "$"))
        );

        return true;
    }

    // --- 3. START FIGHT ---
    public void startFight() {
        if (chicken1 == null || chicken2 == null || chicken1.isDead() || chicken2.isDead()) {
            if (arenaCenter != null && (chicken1 == null || chicken1.isDead())) spawnFighters(arenaCenter);
            else return;
        }

        isRunning = true;
        canBet = false;

        double totalRed = redBets.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalBlue = blueBets.values().stream().mapToDouble(Double::doubleValue).sum();

        Bukkit.broadcast(Component.text("====== KHÓA SỔ CƯỢC ======").color(NamedTextColor.GOLD));
        Bukkit.broadcast(Component.text("Tổng tiền Đội Đỏ:  ").color(NamedTextColor.RED).append(Component.text(Math.round(totalRed) + "$").color(NamedTextColor.YELLOW)));
        Bukkit.broadcast(Component.text("Tổng tiền Đội Xanh: ").color(NamedTextColor.BLUE).append(Component.text(Math.round(totalBlue) + "$").color(NamedTextColor.YELLOW)));
        Bukkit.broadcast(Component.text("Tổng Quỹ (Jackpot): ").color(NamedTextColor.LIGHT_PURPLE).append(Component.text(Math.round(totalRed + totalBlue) + "$").color(NamedTextColor.GREEN)));
        Bukkit.broadcast(Component.text("==========================").color(NamedTextColor.GOLD));

        setupWarrior(chicken1, chicken2);
        setupWarrior(chicken2, chicken1);
        startSkillScheduler();
    }

    public void restartMatch() {
        if (backupBlocks.isEmpty() || arenaCenter == null) return;

        if (isRunning) refundBets();

        isRunning = false;
        if (skillTask != null) skillTask.cancel();
        if (chicken1 != null) chicken1.remove();
        if (chicken2 != null) chicken2.remove();

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

    // --- 4. SKILL SYSTEM (CẬP NHẬT 8 SKILL) ---
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

        // Random từ 0 đến 7 (8 loại skill)
        int skillType = random.nextInt(8);
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

            // --- SKILL MỚI ---

            case 4: // Hồi Phục (Healing)
                world.playSound(caster.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2f);
                world.spawnParticle(Particle.HEART, caster.getLocation().add(0, 1, 0), 5);

                double maxHP = caster.getAttribute(Attribute.MAX_HEALTH).getValue();
                double newHP = Math.min(maxHP, caster.getHealth() + 8.0); // Hồi 4 tim
                caster.setHealth(newHP);

                sendMessage(caster, " tự Hồi Phục!");
                break;

            case 5: // Phun Lửa (Fire Breath)
                world.playSound(caster.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1f);
                world.spawnParticle(Particle.FLAME, target.getLocation(), 10, 0.2, 0.5, 0.2, 0.05);
                target.setFireTicks(60); // Cháy 3 giây
                target.damage(2.0, caster);
                sendMessage(caster, " phun Lửa!");
                break;

            case 6: // Hút Hồn (Life Steal)
                world.playSound(caster.getLocation(), Sound.ENTITY_WITCH_DRINK, 0.8f, 0.5f);
                // Hiệu ứng hút máu từ địch về mình
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, target.getLocation(), 10);
                world.spawnParticle(Particle.HEART, caster.getLocation(), 3);

                target.damage(3.0, caster); // Trừ máu địch
                double stealHP = Math.min(caster.getAttribute(Attribute.MAX_HEALTH).getValue(), caster.getHealth() + 3.0);
                caster.setHealth(stealHP); // Cộng máu mình

                sendMessage(caster, " dùng Hút Hồn!");
                break;

            case 7: // Cú Mổ Độc (Poison Peck)
                world.playSound(caster.getLocation(), Sound.ENTITY_SPIDER_STEP, 1f, 0.5f);
                // Particle màu xanh lá cây (Happy Villager là ngôi sao xanh)
                world.spawnParticle(Particle.HAPPY_VILLAGER, target.getLocation().add(0, 0.5, 0), 10);
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0)); // Độc I trong 3s
                target.damage(2.0, caster);
                sendMessage(caster, " tung Cú Mổ Độc!");
                break;
        }
    }

    private void sendMessage(Chicken c, String action) {
        c.getWorld().sendMessage(c.customName().append(Component.text(action).color(NamedTextColor.YELLOW)));
    }

    // --- 5. END & PAYOUT ---
    public void endFight() {
        if (isRunning || (!redBets.isEmpty() || !blueBets.isEmpty())) {
            refundBets();
        }

        isRunning = false; canBet = false;
        if (skillTask != null) skillTask.cancel();
        if (chicken1 != null) chicken1.remove();
        if (chicken2 != null) chicken2.remove();
        chicken1 = null; chicken2 = null;

        for (Map.Entry<Location, BlockData> entry : backupBlocks.entrySet()) {
            entry.getKey().getBlock().setBlockData(entry.getValue());
        }
        backupBlocks.clear(); arenaCenter = null;
        Bukkit.broadcast(Component.text("Sân đấu đã đóng.").color(NamedTextColor.GREEN));
    }

    // --- HÀM HOÀN TIỀN (REFUND) ---
    private void refundBets() {
        Economy eco = CockfightPlugin.getEconomy();
        if (eco == null) return;
        if (redBets.isEmpty() && blueBets.isEmpty()) return;

        boolean refunded = false;

        for (Map.Entry<UUID, Double> entry : redBets.entrySet()) {
            OfflinePlayer p = Bukkit.getOfflinePlayer(entry.getKey());
            eco.depositPlayer(p, entry.getValue());
            refunded = true;
        }

        for (Map.Entry<UUID, Double> entry : blueBets.entrySet()) {
            OfflinePlayer p = Bukkit.getOfflinePlayer(entry.getKey());
            eco.depositPlayer(p, entry.getValue());
            refunded = true;
        }

        redBets.clear();
        blueBets.clear();

        if (refunded) {
            Bukkit.broadcast(Component.text("⚠ Trận đấu bị hủy! Đã hoàn tiền cược cho tất cả mọi người.").color(NamedTextColor.RED));
        }
    }

    public void forceEnd() { endFight(); }
    public boolean isGameRunning() { return isRunning; }

    public void onChickenDeath(Entity deadChicken) {
        if (!isRunning) return;

        String winnerSide = "";
        Chicken winnerChicken = null;

        if (deadChicken.equals(chicken1)) {
            winnerSide = "blue";
            winnerChicken = chicken2;
        } else if (deadChicken.equals(chicken2)) {
            winnerSide = "red";
            winnerChicken = chicken1;
        }

        if (winnerChicken != null) {
            announceWinner(winnerChicken);
            processPayout(winnerSide);
        }
    }

    private void processPayout(String winnerSide) {
        Economy eco = CockfightPlugin.getEconomy();
        if (eco == null) return;

        double totalRed = redBets.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalBlue = blueBets.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalPool = totalRed + totalBlue;

        Map<UUID, Double> winners = winnerSide.equals("red") ? redBets : blueBets;
        Map<UUID, Double> losers = winnerSide.equals("red") ? blueBets : redBets;

        double totalWinningBets = winnerSide.equals("red") ? totalRed : totalBlue;

        if (winners.isEmpty()) {
            Bukkit.broadcast(Component.text("Nhà cái thắng toàn bộ (Không ai đặt bên thắng)!").color(NamedTextColor.GRAY));
        } else {
            Bukkit.broadcast(Component.text("--- TRẢ THƯỞNG (Phí sàn 5%) ---").color(NamedTextColor.GOLD));

            for (Map.Entry<UUID, Double> entry : winners.entrySet()) {
                UUID uid = entry.getKey();
                double myBet = entry.getValue();

                double grossPayout = 0;
                if (totalWinningBets > 0) {
                    grossPayout = (myBet / totalWinningBets) * totalPool;
                }
                double finalPayout = grossPayout * 0.95;

                OfflinePlayer p = Bukkit.getOfflinePlayer(uid);
                eco.depositPlayer(p, finalPayout);

                if (p.isOnline() && p.getPlayer() != null) {
                    p.getPlayer().sendMessage(Component.text("Chúc mừng! Bạn nhận được: ")
                            .color(NamedTextColor.GREEN)
                            .append(Component.text(Math.round(finalPayout) + "$").color(NamedTextColor.GOLD))
                            .append(Component.text(" (Đã trừ 5% phí)").color(NamedTextColor.GRAY)));
                }
            }
        }

        for (UUID uid : losers.keySet()) {
            OfflinePlayer p = Bukkit.getOfflinePlayer(uid);
            if (p.isOnline() && p.getPlayer() != null) {
                p.getPlayer().sendMessage(Component.text("Rất tiếc! Chiến kê bạn chọn đã thua.").color(NamedTextColor.RED));
                p.getPlayer().sendMessage(Component.text("Cảm ơn đã tham gia và chúc may mắn lần sau!").color(NamedTextColor.GRAY));
            }
        }

        redBets.clear();
        blueBets.clear();
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