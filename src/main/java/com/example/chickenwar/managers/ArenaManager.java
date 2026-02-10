package com.example.chickenwar.managers;

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
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ArenaManager {
    private final Map<Location, BlockData> backupBlocks = new HashMap<>();
    private Location arenaCenter;
    private Chicken chicken1;
    private Chicken chicken2;
    private UUID hostUUID;

    public boolean buildArena(Location center, UUID hostId) {
        if (!backupBlocks.isEmpty()) return false;

        this.hostUUID = hostId;
        this.arenaCenter = center.getBlock().getLocation();

        int radiusX = 12; // Dài 25 (Trục X)
        int radiusZ = 7;  // Rộng 15 (Trục Z)
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
                    floorBlock.setType(Material.POLISHED_BLACKSTONE_BRICKS);
                } else {
                    floorBlock.setType(Material.QUARTZ_BRICKS);
                }

                for (int y = 0; y <= height; y++) {
                    Block currentBlock = world.getBlockAt(cx + x, cy + y, cz + z);
                    saveBlock(currentBlock);

                    boolean isBorder = (x == -radiusX || x == radiusX || z == -radiusZ || z == radiusZ);
                    boolean isCeiling = (y == height);

                    if (isBorder) {
                        if (y == 0) currentBlock.setType(Material.OAK_FENCE);
                        else currentBlock.setType(Material.BARRIER);
                    } else if (isCeiling) {
                        currentBlock.setType(Material.BARRIER);
                    } else {
                        currentBlock.setType(Material.AIR);
                    }
                }
            }
        }

        spawnFighters();
        return true;
    }

    private void saveBlock(Block block) {
        if (!backupBlocks.containsKey(block.getLocation())) {
            backupBlocks.put(block.getLocation(), block.getBlockData());
        }
    }

    public void spawnFighters() {
        if (arenaCenter == null) return;
        removeChickens();

        // --- CẤU HÌNH SKIN GÀ ---
        // Gà 1 (Đỏ) -> Skin NÓNG (WARM)
        chicken1 = createChicken(
                arenaCenter.clone().add(-9, 0, 0),
                "Chiến Kê Đỏ (RED)",
                NamedTextColor.RED,
                Chicken.Variant.WARM
        );

        // Gà 2 (Xanh) -> Skin LẠNH (COLD)
        chicken2 = createChicken(
                arenaCenter.clone().add(9, 0, 0),
                "Chiến Kê Xanh (BLUE)",
                NamedTextColor.BLUE,
                Chicken.Variant.COLD
        );

        lookAt(chicken1, chicken2.getLocation());
        lookAt(chicken2, chicken1.getLocation());
    }

    // --- CẬP NHẬT HÀM: Nhận thêm Chicken.Variant ---
    private Chicken createChicken(Location loc, String name, NamedTextColor color, Chicken.Variant variant) {
        Chicken c = (Chicken) loc.getWorld().spawnEntity(loc, EntityType.CHICKEN);

        // Set skin theo tham số truyền vào
        try {
            c.setVariant(variant);
        } catch (NoSuchMethodError | Exception e) {
            // Bỏ qua nếu server cũ
        }

        c.customName(Component.text(name).color(color));
        c.setCustomNameVisible(true);
        c.setAI(false);
        c.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 4));

        var hpAttr = c.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) hpAttr.setBaseValue(100.0);
        c.setHealth(100.0);
        return c;
    }

    private void lookAt(org.bukkit.entity.Entity entity, Location target) {
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
        hostUUID = null;
    }

    public Chicken getChicken1() { return chicken1; }
    public Chicken getChicken2() { return chicken2; }
    public boolean hasArena() { return !backupBlocks.isEmpty(); }
    public UUID getHostUUID() { return hostUUID; }
}