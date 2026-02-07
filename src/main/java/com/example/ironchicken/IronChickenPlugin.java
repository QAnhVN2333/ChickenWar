package com.example.ironchicken;

import org.bukkit.plugin.java.JavaPlugin;

public class IronChickenPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new ChickenListener(this), this);
        getLogger().info("IronChicken da san sang!");
    }
}