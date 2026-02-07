package com.example.cockfight;

import org.bukkit.plugin.java.JavaPlugin;

public class CockfightPlugin extends JavaPlugin {

    private GameManager gameManager;

    @Override
    public void onEnable() {
        // 1. Tạo file config.yml nếu chưa có
        saveDefaultConfig();

        // 2. Khởi tạo Game Manager
        this.gameManager = new GameManager(this);

        // 3. Đăng ký lệnh & Sự kiện
        getCommand("cockfight").setExecutor(new CockfightCommand(this, gameManager)); // Truyền thêm plugin vào Command
        getServer().getPluginManager().registerEvents(new GameListener(gameManager), this);

        getLogger().info("Cockfight Arena 2.2 (Reloadable) da san sang!");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.forceEnd();
        }
    }
}