package com.example.chickenwar.managers;

import com.example.chickenwar.ChickenWarPlugin;
import com.example.chickenwar.utils.MessageUtils;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

public class SkillManager {
    private final ChickenWarPlugin plugin;
    private final Random random = new Random();

    public SkillManager(ChickenWarPlugin plugin) {
        this.plugin = plugin;
    }

    public void tryCastSkill(Chicken caster, LivingEntity target) {
        // Đọc tỉ lệ từ config, mặc định là 0.7 (70%) thay vì 0.5 như cũ cho nhiều skill hơn
        double chance = plugin.getConfig().getDouble("skill-chance", 0.7);
        if (random.nextDouble() > chance) return;

        int skillType = random.nextInt(8);
        World world = caster.getWorld();

        // Lấy max HP an toàn
        double maxHP = 20.0;
        var attr = caster.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) maxHP = attr.getValue();

        switch (skillType) {
            case 0: // Lôi Điểu (Buff damage lên 4)
                world.playSound(caster.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 2f);
                world.spawnParticle(Particle.CRIT, target.getLocation().add(0, 0.5, 0), 10);
                target.damage(4.0, caster);
                broadcastSkill(caster, "⚡ LÔI ĐIỂU ⚡");
                break;
            case 1: // Cú Đá Xoáy (Damage 5)
                world.playSound(caster.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 2f);
                world.spawnParticle(Particle.CLOUD, target.getLocation(), 10, 0.2, 0.2, 0.2, 0.1);
                target.setVelocity(target.getLocation().toVector().subtract(caster.getLocation().toVector()).normalize().multiply(1.0).setY(0.8));
                target.damage(5.0, caster);
                broadcastSkill(caster, "🌪 CÚ ĐÁ XOÁY 🌪");
                break;
            case 2: // Bom Trứng (Damage 6)
                world.playSound(caster.getLocation(), Sound.ENTITY_EGG_THROW, 1f, 0.5f);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, target.getLocation(), 1);
                world.createExplosion(target.getLocation(), 0F, false);
                target.damage(6.0, caster);
                broadcastSkill(caster, "🥚 BOM TRỨNG 🥚");
                break;
            case 3: // Sonic (Damage 7)
                world.playSound(caster.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.5f);
                world.spawnParticle(Particle.SWEEP_ATTACK, caster.getLocation().add(0, 0.5, 0), 1);
                target.damage(7.0, caster);
                target.setVelocity(caster.getLocation().getDirection().multiply(1.5));
                broadcastSkill(caster, "🔊 TIẾNG GÁY SONIC 🔊");
                break;
            case 4: // Hồi Phục (NERF: Giảm từ 8 xuống 4)
                world.playSound(caster.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2f);
                world.spawnParticle(Particle.HEART, caster.getLocation().add(0, 1, 0), 5);
                caster.setHealth(Math.min(maxHP, caster.getHealth() + 4.0)); // Chỉ hồi 2 tim
                broadcastSkill(caster, "❤ HỒI PHỤC ❤");
                break;
            case 5: // Phun Lửa
                world.playSound(caster.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1f);
                world.spawnParticle(Particle.FLAME, target.getLocation(), 10, 0.2, 0.5, 0.2, 0.05);
                target.setFireTicks(60);
                target.damage(3.0, caster);
                broadcastSkill(caster, "🔥 PHUN LỬA 🔥");
                break;
            case 6: // Hút Hồn
                world.playSound(caster.getLocation(), Sound.ENTITY_WITCH_DRINK, 0.8f, 0.5f);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, target.getLocation(), 10);
                world.spawnParticle(Particle.HEART, caster.getLocation(), 3);
                target.damage(4.0, caster);
                caster.setHealth(Math.min(maxHP, caster.getHealth() + 3.0));
                broadcastSkill(caster, "👻 HÚT HỒN 👻");
                break;
            case 7: // Cú Mổ Độc
                world.playSound(caster.getLocation(), Sound.ENTITY_SPIDER_STEP, 1f, 0.5f);
                world.spawnParticle(Particle.HAPPY_VILLAGER, target.getLocation().add(0, 0.5, 0), 10);
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 1)); // Poison II
                target.damage(3.0, caster);
                broadcastSkill(caster, "☠ CÚ MỔ ĐỘC ☠");
                break;
        }
    }

    private void broadcastSkill(Chicken c, String skillName) {
        String name = c.customName() != null ? ((net.kyori.adventure.text.TextComponent)c.customName()).content() : "Gà Chiến";
        MessageUtils.broadcastActionBar("§e" + name + " §f dùng §6" + skillName);
    }
}