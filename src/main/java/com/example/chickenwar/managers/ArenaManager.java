package com.example.chickenwar.managers;

import com.example.chickenwar.ChickenWarPlugin;
import com.example.chickenwar.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;

public class ArenaManager {
    private final Map<Location, BlockData> backupBlocks = new HashMap<>();
    private Location arenaCenter;
    private Chicken chicken1; // Red
    private Chicken chicken2; // Blue

    public void buildArena(Location center) {
        if (!backupBlocks.isEmpty()) return; // Đã có sân

        this.arenaCenter = center;
        int radius = 4;
        int height = 5;
        World world = center.getWorld();
        int cx = center.getBlockX(); int cy = center.getBlockY(); int cz = center.getBlockZ();

        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                saveAndSetBlock(world.getBlockAt(x, cy - 1, z), Material.POLISHED_ANDESITE);
                for (int y = 0; y <= height; y++) {
                    Block block = world.getBlockAt(x, cy + y, z);
                    if (x == cx - radius || x == cx + radius || z == cz - radius || z == cz + radius) {
                        saveAndSetBlock(block, Material.OAK_FENCE);
                    } else if (y == height) {
                        saveAndSetBlock(block, Material.BARRIER);
                    } else {
                        saveAndSetBlock(block, Material.AIR);
                    }
                }
            }
        }
        spawnFighters();
    }

    private void saveAndSetBlock(Block block, Material newMaterial) {
        if (!backupBlocks.containsKey(block.getLocation())) {
            backupBlocks.put(block.getLocation(), block.getBlockData());
        }
        block.setType(newMaterial);
    }

    public void spawnFighters() {
        if (arenaCenter == null) return;
        // Xóa gà cũ nếu còn sót
        removeChickens();

        chicken1 = createChicken(arenaCenter.clone().add(-2, 0, 0), "Chiến Kê Đỏ (RED)", NamedTextColor.RED);
        chicken2 = createChicken(arenaCenter.clone().add(2, 0, 0), "Chiến Kê Xanh (BLUE)", NamedTextColor.BLUE);
    }

    private Chicken createChicken(Location loc, String name, NamedTextColor color) {
        Chicken c = (Chicken) loc.getWorld().spawnEntity(loc, EntityType.CHICKEN);
        c.customName(Component.text(name).color(color));
        c.setCustomNameVisible(true);
        c.setAI(false); // Đóng băng
        c.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 4));

        // Set Max Health luôn ở đây cho chắc
        var hpAttr = c.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) hpAttr.setBaseValue(100.0); // Giá trị mặc định, GameManager sẽ set lại theo config sau
        c.setHealth(100.0);

        return c;
    }

    public void removeChickens() {
        if (chicken1 != null) chicken1.remove();
        if (chicken2 != null) chicken2.remove();
        chicken1 = null; chicken2 = null;
    }

    public void clearArena() {
        removeChickens();
        for (Map.Entry<Location, BlockData> entry : backupBlocks.entrySet()) {
            entry.getKey().getBlock().setBlockData(entry.getValue());
        }
        backupBlocks.clear();
        arenaCenter = null;
    }

    // Getters
    public Chicken getChicken1() { return chicken1; }
    public Chicken getChicken2() { return chicken2; }
    public boolean hasArena() { return !backupBlocks.isEmpty(); }
}