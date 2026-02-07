package com.example.cockfight;

import org.bukkit.entity.Chicken;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class GameListener implements Listener {

    private final GameManager gameManager;

    public GameListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Chicken) {
            gameManager.onChickenDeath(event.getEntity());
        }
    }

    // Đã xóa phần onAttack setDamage(4.0) để tránh xung đột với Skill
    // AI của GameManager đã tự xử lý việc gây damage rồi.
}