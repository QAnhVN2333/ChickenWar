package com.example.chickenwar;

import com.example.chickenwar.managers.ConfigManager;
import com.example.chickenwar.managers.GameManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class ChickenWarPlugin extends JavaPlugin {

    private GameManager gameManager;
    private static Economy economy = null;
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        // 1. Setup Vault Economy
        if (!setupEconomy()) {
            getLogger().warning("⚠ Không tìm thấy Vault hoặc plugin kinh tế! Tính năng cá cược sẽ bị tắt.");
        } else {
            getLogger().info("✔ Đã kết nối hệ thống tiền tệ thành công.");
        }

        // 2. Load and merge config/messages files
        this.configManager = new ConfigManager(this);

        // 3. Init game manager
        this.gameManager = new GameManager(this);

        // 4. Register command
        PluginCommand cmd = getCommand("chickenwar");
        if (cmd != null) {
            cmd.setExecutor(new ChickenWarCommand(this, gameManager));
            cmd.setTabCompleter(new ChickenWarTabCompleter(this));
        } else {
            getLogger().severe("LỖI: Không tìm thấy lệnh 'chickenwar' trong plugin.yml!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 5. Register events
        getServer().getPluginManager().registerEvents(new GameListener(this, gameManager), this);

        getLogger().info("ChickenWar 3.1 (SOLID Architecture) da san sang!");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.forceEnd(); // Dọn dẹp sân bãi và hoàn tiền nếu server tắt
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

    public ConfigManager getConfigManager() {
        return configManager;
    }
}