package com.example.cockfight;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class CockfightPlugin extends JavaPlugin {

    private GameManager gameManager;
    private static Economy economy = null; // Biến lưu trữ hệ thống tiền tệ

    @Override
    public void onEnable() {
        // 1. Setup Economy (Vault)
        if (!setupEconomy()) {
            getLogger().severe("Không tìm thấy plugin Vault hoặc plugin tiền tệ (Essentials, CMI...)!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        this.gameManager = new GameManager(this);

        getCommand("cockfight").setExecutor(new CockfightCommand(this, gameManager));
        getServer().getPluginManager().registerEvents(new GameListener(gameManager), this);

        getLogger().info("Cockfight 3.0 (Casino) da san sang!");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.forceEnd();
        }
    }

    // Hàm kết nối Vault
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    // Getter để GameManager dùng
    public static Economy getEconomy() {
        return economy;
    }
}