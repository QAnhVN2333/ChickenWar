package com.example.chickenwar;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class ChickenWarPlugin extends JavaPlugin {

    private GameManager gameManager;
    private static Economy economy = null;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().warning("⚠ Không tìm thấy Vault! Tính năng cá cược sẽ bị tắt.");
        } else {
            getLogger().info("✔ Đã kết nối hệ thống tiền tệ.");
        }

        saveDefaultConfig();
        this.gameManager = new GameManager(this);

        // --- FIX NPE: Kiểm tra lệnh trước khi đăng ký ---
        PluginCommand command = getCommand("chickenwar");
        if (command != null) {
            command.setExecutor(new ChickenWarCommand(this, gameManager));
        } else {
            getLogger().severe("LỖI NGHIÊM TRỌNG: Không tìm thấy lệnh 'chickenwar' trong plugin.yml!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new GameListener(gameManager), this);

        getLogger().info("ChickenWar 3.0 (Safe Version) da san sang!");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.forceEnd();
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public static Economy getEconomy() {
        return economy;
    }
}