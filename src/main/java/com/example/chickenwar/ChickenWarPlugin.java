package com.example.chickenwar;

import com.example.chickenwar.managers.GameManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class ChickenWarPlugin extends JavaPlugin {

    private GameManager gameManager;
    private static Economy economy = null;

    @Override
    public void onEnable() {
        // 1. Setup Vault Economy
        if (!setupEconomy()) {
            getLogger().warning("⚠ Không tìm thấy Vault hoặc plugin kinh tế! Tính năng cá cược sẽ bị tắt.");
        } else {
            getLogger().info("✔ Đã kết nối hệ thống tiền tệ thành công.");
        }

        // 2. Load Config
        saveDefaultConfig();

        // 3. Khởi tạo Game Manager (Nhạc trưởng)
        this.gameManager = new GameManager(this);

        // 4. Đăng ký lệnh (Kiểm tra null để tránh lỗi)
        PluginCommand cmd = getCommand("chickenwar");
        if (cmd != null) {
            cmd.setExecutor(new ChickenWarCommand(this, gameManager));
        } else {
            getLogger().severe("LỖI: Không tìm thấy lệnh 'chickenwar' trong plugin.yml!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 5. Đăng ký Sự kiện
        getServer().getPluginManager().registerEvents(new GameListener(gameManager), this);

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
}