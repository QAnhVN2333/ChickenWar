package com.example.chickenwar;

import com.example.chickenwar.managers.BetManager;
import com.example.chickenwar.managers.ConfigManager;
import com.example.chickenwar.managers.GameManager;
import com.example.chickenwar.utils.MessageUtils;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Map;

public class GameListener implements Listener {

    private final ChickenWarPlugin plugin;
    private final GameManager gameManager;

    public GameListener(ChickenWarPlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFighterDamageByEntity(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!gameManager.isFighter(victim)) {
            return;
        }

        Entity damager = resolveDamager(event.getDamager());
        // Only the 2 active fighter chickens can damage each other.
        if (!gameManager.canDamageFighter(damager, victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFighterEnvironmentDamage(EntityDamageEvent event) {
        if (!gameManager.isFighter(event.getEntity())) {
            return;
        }

        // Block environmental/magic/poison/fire damage on fighter chickens.
        if (!(event instanceof EntityDamageByEntityEvent)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFighterCombust(EntityCombustEvent event) {
        if (gameManager.isFighter(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Chicken chicken)) {
            return;
        }

        if (gameManager.isFighter(chicken)) {
            // Ensure no meat/feather/exp drops from fighter chickens.
            event.getDrops().clear();
            event.setDroppedExp(0);
        }

        gameManager.onChickenDeath(chicken);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFighterDropItem(EntityDropItemEvent event) {
        if (gameManager.isFighter(event.getEntity())) {
            // Cancel egg laying item drop from fighter chickens.
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        BetManager betManager = gameManager.getBetManager();
        if (!betManager.hasPendingClaim(event.getPlayer().getUniqueId())) {
            return;
        }

        long amount = betManager.getPendingClaim(event.getPlayer().getUniqueId());
        MessageUtils.send(event.getPlayer(), msg("command.claim-pending-on-join", Map.of("amount", String.valueOf(amount))), NamedTextColor.GOLD);
    }

    private Entity resolveDamager(Entity rawDamager) {
        if (rawDamager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return rawDamager;
    }

    private String msg(String path, Map<String, String> placeholders) {
        ConfigManager configManager = plugin.getConfigManager();
        if (configManager == null) {
            return path;
        }
        return configManager.getMessage(path, placeholders);
    }
}