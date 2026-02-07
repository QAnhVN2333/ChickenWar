package com.example.ironchicken;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public class ChickenListener implements Listener {

    private final IronChickenPlugin plugin;
    private final NamespacedKey ironFedKey;

    public ChickenListener(IronChickenPlugin plugin) {
        this.plugin = plugin;
        this.ironFedKey = new NamespacedKey(plugin, "is_iron_fed");
    }

    @EventHandler
    public void onFeedIron(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Chicken chicken)) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() != Material.IRON_INGOT) return;

        if (chicken.getPersistentDataContainer().has(ironFedKey, PersistentDataType.BYTE)) {
            player.sendMessage(Component.text("Gà này đã ăn sắt rồi!").color(NamedTextColor.RED));
            return;
        }

        event.setCancelled(true);
        item.setAmount(item.getAmount() - 1);

        // Đánh dấu gà
        chicken.getPersistentDataContainer().set(ironFedKey, PersistentDataType.BYTE, (byte) 1);

        // --- CẬP NHẬT CHỈ SỐ ---

        // 1. Máu tối đa (30 HP = 15 Tim)
        var healthAttr = chicken.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(30.0);
            chicken.setHealth(30.0);
        }

        // 2. Kích thước (Dùng Registry để lấy Attribute SCALE chuẩn nhất)
        Attribute scaleType = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("scale"));
        if (scaleType != null) {
            var scaleAttr = chicken.getAttribute(scaleType);
            if (scaleAttr != null) scaleAttr.setBaseValue(1.2);
        }

        // LƯU Ý: Không set ATTACK_DAMAGE vì gà không có attribute này (Gây lỗi Crash)

        // --- AI LOGIC ---
        Bukkit.getMobGoals().removeGoal(chicken, VanillaGoal.PANIC);
        Bukkit.getMobGoals().addGoal(chicken, 1, new IronAttackGoal(chicken));
        Bukkit.getMobGoals().addGoal(chicken, 2, new IronTargetGoal(plugin, chicken));

        // --- HIỆU ỨNG ---
        chicken.getWorld().playSound(chicken.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.5f);
        chicken.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, chicken.getLocation().add(0, 0.5, 0), 20);
        chicken.customName(Component.text("Chiến Kê [Sắt]")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.BOLD, true));
        chicken.setCustomNameVisible(true);

        player.sendMessage(Component.text("Nâng cấp thành công!").color(NamedTextColor.GREEN));
    }

    // --- CLASS AI 1: TẤN CÔNG (ĐÃ FIX CRASH) ---
    public static class IronAttackGoal implements Goal<Chicken> {
        private final Chicken chicken;
        private final GoalKey<Chicken> key;

        public IronAttackGoal(Chicken chicken) {
            this.chicken = chicken;
            this.key = GoalKey.of(Chicken.class, new NamespacedKey("ironchicken", "attack_goal"));
        }

        @Override
        public boolean shouldActivate() {
            return chicken.getTarget() != null && !chicken.getTarget().isDead();
        }

        @Override
        public void tick() {
            LivingEntity target = chicken.getTarget();
            if (target == null) return;

            chicken.getPathfinder().moveTo(target, 1.5);

            // Nếu đủ gần thì đánh
            if (chicken.getLocation().distanceSquared(target.getLocation()) < 2.0) {
                // FIX: Dùng damage() thay vì attack()
                // Gây 4 sát thương (2 tim) lên kẻ thù, nguồn là con gà này
                target.damage(4.0, chicken);
                chicken.swingMainHand();
            }
        }

        @Override
        public @NotNull GoalKey<Chicken> getKey() { return key; }

        @Override
        public @NotNull EnumSet<GoalType> getTypes() { return EnumSet.of(GoalType.TARGET, GoalType.MOVE); }
    }

    // --- CLASS AI 2: TÌM MỤC TIÊU ---
    public static class IronTargetGoal implements Goal<Chicken> {
        private final Chicken chicken;
        private final GoalKey<Chicken> key;
        private final NamespacedKey ironFedKey;

        public IronTargetGoal(IronChickenPlugin plugin, Chicken chicken) {
            this.chicken = chicken;
            this.key = GoalKey.of(Chicken.class, new NamespacedKey("ironchicken", "target_goal"));
            this.ironFedKey = new NamespacedKey(plugin, "is_iron_fed");
        }

        @Override
        public boolean shouldActivate() {
            for (Entity entity : chicken.getNearbyEntities(10, 5, 10)) {
                if (entity instanceof Chicken other && entity != chicken) {
                    if (other.getPersistentDataContainer().has(ironFedKey, PersistentDataType.BYTE)) {
                        chicken.setTarget(other);
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public @NotNull GoalKey<Chicken> getKey() { return key; }

        @Override
        public @NotNull EnumSet<GoalType> getTypes() { return EnumSet.of(GoalType.TARGET); }
    }
}