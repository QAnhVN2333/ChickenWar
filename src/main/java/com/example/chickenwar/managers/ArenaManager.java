package com.example.chickenwar.managers;

import com.example.chickenwar.ChickenWarPlugin;
import com.example.chickenwar.config.ConfigKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ArenaManager {
    public static final String FIGHTER_TAG = "cw_fighter";
    public static final String RED_TAG = "cw_red";
    public static final String BLUE_TAG = "cw_blue";

    private final Map<Location, BlockData> backupBlocks = new HashMap<>();
    private final ChickenWarPlugin plugin;

    private Location arenaCenter;
    private Chicken chicken1;
    private Chicken chicken2;

    public ArenaManager(ChickenWarPlugin plugin) {
        this.plugin = plugin;
    }

    public Material getMaterial(String name) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Cannot resolve material: " + name + ". Fallback to GRASS_BLOCK.");
            return Material.GRASS_BLOCK;
        }
    }

    public boolean buildArena(Location center) {
        if (!backupBlocks.isEmpty() || center == null || center.getWorld() == null) {
            return false;
        }

        this.arenaCenter = center.getBlock().getLocation();

        int radiusX = 12;
        int radiusZ = 7;
        int height = 7;

        World world = arenaCenter.getWorld();
        int cx = arenaCenter.getBlockX();
        int cy = arenaCenter.getBlockY();
        int cz = arenaCenter.getBlockZ();

        for (int x = -radiusX; x <= radiusX; x++) {
            for (int z = -radiusZ; z <= radiusZ; z++) {
                Block floorBlock = world.getBlockAt(cx + x, cy - 1, cz + z);
                saveBlock(floorBlock);

                if ((Math.abs(x) + Math.abs(z)) % 2 == 0) {
                    floorBlock.setType(getMaterial(plugin.getConfig().getString(ConfigKeys.Arena.FLOOR_MATERIAL_EVEN, "GRASS_BLOCK")));
                } else {
                    floorBlock.setType(getMaterial(plugin.getConfig().getString(ConfigKeys.Arena.FLOOR_MATERIAL_ODD, "GRASS_BLOCK")));
                }

                for (int y = 0; y <= height; y++) {
                    Block currentBlock = world.getBlockAt(cx + x, cy + y, cz + z);
                    saveBlock(currentBlock);

                    boolean isBorder = (x == -radiusX || x == radiusX || z == -radiusZ || z == radiusZ);
                    boolean isCeiling = (y == height);

                    if (isBorder) {
                        FileConfiguration config = plugin.getConfig();
                        if (y == 0) {
                            currentBlock.setType(getMaterial(config.getString(ConfigKeys.Arena.FLOOR_FENCE, "OAK_FENCE")));
                        } else {
                            currentBlock.setType(Material.BARRIER);
                        }
                    } else if (isCeiling) {
                        currentBlock.setType(Material.BARRIER);
                    } else {
                        currentBlock.setType(Material.AIR);
                    }
                }
            }
        }

        saveBackupToDisk();
        spawnFighters();
        return true;
    }

    private void saveBlock(Block block) {
        backupBlocks.putIfAbsent(block.getLocation(), block.getBlockData());
    }

    public void spawnFighters() {
        if (arenaCenter == null) {
            return;
        }

        removeChickens();

        String redName = plugin.getConfig().getString(ConfigKeys.Arena.CHICKEN1_NAME, "Chien Ke Do");
        String blueName = plugin.getConfig().getString(ConfigKeys.Arena.CHICKEN2_NAME, "Chien Ke Xanh");

        chicken1 = createChicken(arenaCenter.clone().add(-9, 0, 0), redName, NamedTextColor.RED, RED_TAG, Chicken.Variant.WARM);
        chicken2 = createChicken(arenaCenter.clone().add(9, 0, 0), blueName, NamedTextColor.BLUE, BLUE_TAG, Chicken.Variant.COLD);

        lookAt(chicken1, chicken2.getLocation());
        lookAt(chicken2, chicken1.getLocation());
    }

    private Chicken createChicken(Location loc, String name, NamedTextColor color, String sideTag, Chicken.Variant variant) {
        Chicken chicken = (Chicken) loc.getWorld().spawnEntity(loc, EntityType.CHICKEN);

        try {
            chicken.setVariant(variant);
        } catch (NoSuchMethodError ignored) {
            // Keep compatibility with older API versions.
        }

        // Mark fighters using scoreboard tags for strict event filtering.
        chicken.addScoreboardTag(FIGHTER_TAG);
        chicken.addScoreboardTag(sideTag);

        chicken.customName(Component.text(name).color(color));
        chicken.setCustomNameVisible(true);
        chicken.setAI(false);
        chicken.setCanPickupItems(false);
        chicken.setAdult();
        chicken.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 4, false, false));

        var hpAttr = chicken.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(100.0);
        }
        chicken.setHealth(100.0);
        return chicken;
    }

    private void lookAt(Entity entity, Location target) {
        Location loc = entity.getLocation();
        double dx = target.getX() - loc.getX();
        double dy = target.getY() - loc.getY();
        double dz = target.getZ() - loc.getZ();

        if (dx != 0) {
            if (dx < 0) loc.setYaw((float) (1.5 * Math.PI));
            else loc.setYaw((float) (0.5 * Math.PI));
            loc.setYaw(loc.getYaw() - (float) Math.atan(dz / dx));
        } else if (dz < 0) {
            loc.setYaw((float) Math.PI);
        }

        double dxz = Math.sqrt(Math.pow(dx, 2) + Math.pow(dz, 2));
        loc.setPitch((float) -Math.atan(dy / dxz));

        loc.setYaw(-loc.getYaw() * 180f / (float) Math.PI);
        loc.setPitch(loc.getPitch() * 180f / (float) Math.PI);

        entity.teleport(loc);
    }

    public boolean isFighter(Entity entity) {
        return entity instanceof Chicken && entity.getScoreboardTags().contains(FIGHTER_TAG);
    }

    public boolean areCurrentOpponents(Entity attacker, Entity victim) {
        if (!(attacker instanceof Chicken) || !(victim instanceof Chicken)) {
            return false;
        }
        if (chicken1 == null || chicken2 == null) {
            return false;
        }
        UUID a = attacker.getUniqueId();
        UUID v = victim.getUniqueId();
        return (a.equals(chicken1.getUniqueId()) && v.equals(chicken2.getUniqueId()))
                || (a.equals(chicken2.getUniqueId()) && v.equals(chicken1.getUniqueId()));
    }

    public void removeChickens() {
        if (chicken1 != null) {
            chicken1.remove();
        }
        if (chicken2 != null) {
            chicken2.remove();
        }
        chicken1 = null;
        chicken2 = null;
    }

    public void clearArena() {
        removeChickens();
        for (Map.Entry<Location, BlockData> entry : backupBlocks.entrySet()) {
            entry.getKey().getBlock().setBlockData(entry.getValue());
        }
        backupBlocks.clear();
        arenaCenter = null;
        deleteBackupFile();
    }

    public void cleanupOrphanFighters() {
        for (World world : Bukkit.getWorlds()) {
            for (Chicken chicken : world.getEntitiesByClass(Chicken.class)) {
                if (chicken.getScoreboardTags().contains(FIGHTER_TAG)) {
                    chicken.remove();
                }
            }
        }
    }

    public void restoreArenaFromDiskIfPresent() {
        File file = getBackupFile();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration backup = YamlConfiguration.loadConfiguration(file);
        String worldName = backup.getString("world");
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            plugin.getLogger().warning("Arena backup found but world is not loaded yet: " + worldName);
            return;
        }

        for (String raw : backup.getStringList("blocks")) {
            String[] split = raw.split("\\|", 4);
            if (split.length != 4) {
                continue;
            }
            int x = Integer.parseInt(split[0]);
            int y = Integer.parseInt(split[1]);
            int z = Integer.parseInt(split[2]);
            String blockData = split[3];
            world.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(blockData));
        }

        deleteBackupFile();
        plugin.getLogger().warning("Recovered arena blocks from disk backup after an unclean shutdown.");
    }

    private void saveBackupToDisk() {
        if (arenaCenter == null || arenaCenter.getWorld() == null || backupBlocks.isEmpty()) {
            return;
        }

        YamlConfiguration out = new YamlConfiguration();
        out.set("world", arenaCenter.getWorld().getName());

        // Persist every original block state so crash recovery can replay safely.
        var lines = backupBlocks.entrySet().stream()
                .map(e -> e.getKey().getBlockX() + "|" + e.getKey().getBlockY() + "|" + e.getKey().getBlockZ() + "|" + e.getValue().getAsString())
                .toList();
        out.set("blocks", lines);

        File file = getBackupFile();
        file.getParentFile().mkdirs();
        try {
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save arena backup file: " + e.getMessage());
        }
    }

    private File getBackupFile() {
        return new File(plugin.getDataFolder(), "arena-backups/current.yml");
    }

    private void deleteBackupFile() {
        File file = getBackupFile();
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Cannot delete arena backup file: " + file.getAbsolutePath());
        }
    }

    public Chicken getChicken1() {
        return chicken1;
    }

    public Chicken getChicken2() {
        return chicken2;
    }

    public boolean hasArena() {
        return !backupBlocks.isEmpty();
    }

    public Location getArenaCenter() {
        return arenaCenter;
    }
}
