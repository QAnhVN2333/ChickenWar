package com.example.chickenwar;

import com.example.chickenwar.managers.GameManager;
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
        // Chỉ quan tâm nếu thực thể chết là Gà
        if (event.getEntity() instanceof Chicken chicken) {
            // Chuyển logic xử lý sang cho GameManager
            gameManager.onChickenDeath(chicken);
        }
    }
}