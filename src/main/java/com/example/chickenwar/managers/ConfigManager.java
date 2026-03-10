package com.example.chickenwar.managers;

import com.example.chickenwar.ChickenWarPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Sound;
import org.bukkit.NamespacedKey;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manager quản lý config.yml và messages.yml
 * Hỗ trợ reload và lấy messages với placeholders
 */
public class ConfigManager {

    private static final String VERSION_KEY = "version";
    private final ChickenWarPlugin plugin;

    // Config cho messages.yml
    private FileConfiguration messagesConfig;

    // MiniMessage để parse màu và format
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Cache prefix để không phải parse mỗi lần
    private String prefix;

    public ConfigManager(ChickenWarPlugin plugin) {
        this.plugin = plugin;

        // Load and merge config.yml
        loadConfig();

        // Load and merge messages.yml
        loadMessages();
    }

    /**
     * Load config.yml from resources and merge new keys by version.
     */
    private void loadConfig() {
        plugin.saveDefaultConfig();

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        FileConfiguration userConfig = plugin.getConfig();
        YamlConfiguration defaultConfig = loadDefaultYamlFromJar("config.yml");

        if (defaultConfig == null) {
            plugin.getLogger().warning("Cannot load default config.yml from jar.");
            return;
        }

        syncVersionedYaml(configFile, userConfig, defaultConfig, "config.yml");
        plugin.reloadConfig();
    }

    /**
     * Load messages.yml from resources, create only when missing, then merge by version.
     */
    private void loadMessages() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        YamlConfiguration defaultMessages = loadDefaultYamlFromJar("messages.yml");
        if (defaultMessages == null) {
            plugin.getLogger().warning("Cannot load default messages.yml from jar.");
            return;
        }

        syncVersionedYaml(messagesFile, messagesConfig, defaultMessages, "messages.yml");
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        prefix = messagesConfig.getString("prefix", "");
    }

    /**
     * Sync file version by add-only merge and then update version value.
     */
    private void syncVersionedYaml(File file, FileConfiguration userConfig, FileConfiguration defaultConfig, String fileLabel) {
        String userVersion = readVersion(userConfig);
        String defaultVersion = readVersion(defaultConfig);

        if (compareVersions(userVersion, defaultVersion) >= 0) {
            return;
        }

        plugin.getLogger().info("Detected newer " + fileLabel + " version (" + userVersion + " -> " + defaultVersion + ").");
        int addedKeys = deepMerge(userConfig, defaultConfig);
        userConfig.set(VERSION_KEY, defaultVersion);

        try {
            userConfig.save(file);
            plugin.getLogger().info("Merged " + addedKeys + " new keys into " + fileLabel + ".");
        } catch (IOException e) {
            plugin.getLogger().severe("Cannot save " + fileLabel + " after merge: " + e.getMessage());
        }
    }

    /**
     * Load default yaml file from plugin jar resources.
     */
    private YamlConfiguration loadDefaultYamlFromJar(String resourceName) {
        InputStream defaultStream = plugin.getResource(resourceName);
        if (defaultStream == null) {
            return null;
        }
        InputStreamReader reader = new InputStreamReader(defaultStream, StandardCharsets.UTF_8);
        return YamlConfiguration.loadConfiguration(reader);
    }

    /**
     * Read version value as integer string. Missing version is treated as 0.
     */
    private String readVersion(ConfigurationSection config) {
        Object raw = config.get(VERSION_KEY);
        if (raw == null) {
            return "0";
        }

        if (raw instanceof Number number) {
            return String.valueOf(number.intValue());
        }

        return String.valueOf(parseVersionToken(String.valueOf(raw)));
    }

    /**
     * Compare integer version values only.
     */
    private int compareVersions(String left, String right) {
        return Integer.compare(parseVersionToken(left), parseVersionToken(right));
    }

    /**
     * Parse integer token safely, ignoring non-digit characters.
     */
    private int parseVersionToken(String token) {
        String cleaned = token.replaceAll("[^0-9]", "");
        if (cleaned.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * Deep merge: add missing keys from source to target without overwriting user values.
     */
    private int deepMerge(FileConfiguration target, FileConfiguration source) {
        return deepMergeSection(target, source, "");
    }

    /**
     * Recursively merge nested sections.
     */
    private int deepMergeSection(ConfigurationSection target, ConfigurationSection source, String path) {
        int addedKeys = 0;

        Set<String> sourceKeys = source.getKeys(false);
        for (String key : sourceKeys) {
            String fullPath = path.isEmpty() ? key : path + "." + key;
            Object sourceValue = source.get(key);

            if (sourceValue instanceof ConfigurationSection sourceSection) {
                if (target.isConfigurationSection(key)) {
                    ConfigurationSection targetSection = target.getConfigurationSection(key);
                    if (targetSection != null) {
                        addedKeys += deepMergeSection(targetSection, sourceSection, fullPath);
                    }
                } else if (!target.contains(key)) {
                    ConfigurationSection created = target.createSection(key);
                    addedKeys += deepMergeSection(created, sourceSection, fullPath);
                }
                continue;
            }

            if (!target.contains(key)) {
                target.set(key, sourceValue);
                addedKeys++;
            }
        }

        return addedKeys;
    }

    /**
     * Reload tất cả config files
     */
    public void reload() {
        // Reload config.yml with merge/version sync
        loadConfig();

        // Reload messages.yml with merge/version sync
        loadMessages();

        plugin.getLogger().info("Đã reload config thành công!");
    }

    /**
     * Lấy message từ messages.yml
     *
     * @param path Đường dẫn đến message trong file
     * @return Message string (chưa parse)
     */
    public String getMessage(String path) {
        String message = messagesConfig.getString("messages." + path, "<red>Message not found: " + path);
        return message.replace("{prefix}", prefix);
    }

    /**
     * Lấy message và thay thế placeholders
     *
     * @param path Đường dẫn đến message
     * @param placeholders Map các placeholder và giá trị thay thế
     * @return Message đã được thay thế placeholders
     */
    public String getMessage(String path, Map<String, String> placeholders) {
        String message = getMessage(path);

        // Thay thế tất cả placeholders
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        return message;
    }

    /**
     * Lấy message với một placeholder duy nhất
     */
    public String getMessage(String path, String placeholder, String value) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put(placeholder, value);
        return getMessage(path, placeholders);
    }

    /**
     * Parse message string thành Component (có màu và format)
     * Sử dụng MiniMessage format
     */
    public Component parseMessage(String message) {
        return miniMessage.deserialize(message);
    }

    /**
     * Lấy message đã được parse thành Component
     */
    public Component getMessageComponent(String path) {
        return parseMessage(getMessage(path));
    }

    /**
     * Lấy message với placeholders đã được parse thành Component
     */
    public Component getMessageComponent(String path, Map<String, String> placeholders) {
        return parseMessage(getMessage(path, placeholders));
    }

    /**
     * Lấy message với một placeholder duy nhất, đã parse thành Component
     */
    public Component getMessageComponent(String path, String placeholder, String value) {
        return parseMessage(getMessage(path, placeholder, value));
    }

    /**
     * Lấy list messages và parse thành list components
     */
    public java.util.List<Component> getMessageList(String path, Map<String, String> placeholders) {
        java.util.List<String> rawList = messagesConfig.getStringList("messages." + path);
        java.util.List<Component> componentList = new java.util.ArrayList<>();

        if (rawList.isEmpty()) {
            // Fallback nếu không tìm thấy list, thử lấy string đơn
            String single = getMessage(path, placeholders);
            if (!single.startsWith("Message not found")) {
                componentList.add(parseMessage(single));
            }
            return componentList;
        }

        for (String line : rawList) {
            // Replace placeholders
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                line = line.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            // Parse prefix
            line = line.replace("{prefix}", prefix);

            componentList.add(parseMessage(line));
        }
        return componentList;
    }

    /**
     * Read raw string from messages.yml using direct path.
     */
    public String getRawString(String path, String defaultValue) {
        return messagesConfig.getString(path, defaultValue);
    }

    /**
     * Read raw list from messages.yml using direct path.
     */
    public List<String> getRawStringList(String path) {
        return messagesConfig.getStringList(path);
    }

    /**
     * Parse title/subtitle section with placeholders into components.
     */
    public Component getRawComponent(String path, Map<String, String> placeholders) {
        String value = getRawString(path, "");
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        value = value.replace("{prefix}", prefix);
        return parseMessage(value);
    }

    /**
     * Parse list section with placeholders into components.
     */
    public java.util.List<Component> getRawComponentList(String path, Map<String, String> placeholders) {
        java.util.List<String> lines = getRawStringList(path);
        java.util.List<Component> output = new java.util.ArrayList<>();
        for (String line : lines) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                line = line.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            line = line.replace("{prefix}", prefix);
            output.add(parseMessage(line));
        }
        return output;
    }

    // ==================== Config Getters ====================

    /**
     * Lấy thời gian countdown trước khi teleport (giây)
     */
    public int getTeleportDelay() {
        return plugin.getConfig().getInt("settings.teleport-delay", 5);
    }

    /**
     * Lấy thời gian request hết hạn (giây)
     */
    public int getRequestExpiry() {
        return plugin.getConfig().getInt("settings.request-expiry", 60);
    }

    /**
     * Kiểm tra có hủy teleport khi di chuyển không
     */
    public boolean isCancelOnMove() {
        return plugin.getConfig().getBoolean("settings.cancel-on-move", true);
    }

    // ==================== World Restriction ====================

    /**
     * Kiểm tra world có được phép sử dụng plugin không (global restriction)
     * Dựa trên cấu hình world-restriction trong config.yml
     *
     * Logic:
     * - Nếu world-restriction.enabled = false → tất cả world đều được phép
     * - Nếu type = whitelist → chỉ các world trong whitelist_worlds mới được phép
     * - Nếu type = blacklist → tất cả world được phép TRỪ các world trong blacklist_worlds
     *
     * @param worldName Tên world cần kiểm tra
     * @return true nếu world được phép
     */
    public boolean isWorldAllowed(String worldName) {
        // Kiểm tra tính năng có bật không
        if (!plugin.getConfig().getBoolean("world-restriction.enabled", false)) {
            return true; // Không bật restriction → cho phép tất cả
        }

        String type = plugin.getConfig().getString("world-restriction.type", "blacklist").toLowerCase();
        List<String> worlds;

        if (type.equals("whitelist")) {
            // Whitelist: chỉ các world trong danh sách mới được phép
            worlds = plugin.getConfig().getStringList("world-restriction.whitelist_worlds");
            return worlds.contains(worldName);
        } else {
            // Blacklist: tất cả world được phép TRỪ các world trong danh sách
            worlds = plugin.getConfig().getStringList("world-restriction.blacklist_worlds");
            return !worlds.contains(worldName);
        }
    }

    /**
     * Kiểm tra world có được phép sử dụng /back không
     * Cấu hình riêng cho /back, nằm trong settings.back.allowed-worlds
     *
     * @param worldName Tên world cần kiểm tra
     * @return true nếu world được phép dùng /back
     */
    public boolean isBackWorldAllowed(String worldName) {
        // Lấy danh sách worlds cho phép /back
        List<String> allowedWorlds = plugin.getConfig().getStringList("settings.back.allowed-worlds");

        // Nếu danh sách trống hoặc chứa "*" → cho phép tất cả worlds
        if (allowedWorlds.isEmpty() || allowedWorlds.contains("*")) {
            return true;
        }

        return allowedWorlds.contains(worldName);
    }

    public boolean isBackOnDeathEnabled() {
        return plugin.getConfig().getBoolean("settings.back.back-on-death", true);
    }

    public boolean isBackOnTeleportEnabled() {
        return plugin.getConfig().getBoolean("settings.back.back-on-teleport", true);
    }

    /**
     * Lấy prefix
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Lấy config chính
     */
    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    /**
     * Lấy messages config
     */
    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    // ==================== Sound Getters ====================

    /**
     * Lấy tên âm thanh từ config
     * @param soundKey Key của âm thanh (vd: "request-sent", "countdown")
     * @return Tên Sound (uppercase với underscore, vd: "UI_BUTTON_CLICK")
     */
    public String getSoundName(String soundKey) {
        return plugin.getConfig().getString("sounds." + soundKey + ".sound", "UI_BUTTON_CLICK");
    }

    /**
     * Lấy volume của âm thanh từ config
     * @param soundKey Key của âm thanh
     * @return Giá trị volume (0.0 - 1.0)
     */
    public float getSoundVolume(String soundKey) {
        return (float) plugin.getConfig().getDouble("sounds." + soundKey + ".volume", 1.0);
    }

    /**
     * Lấy pitch của âm thanh từ config
     * @param soundKey Key của âm thanh
     * @return Giá trị pitch (0.5 - 2.0)
     */
    public float getSoundPitch(String soundKey) {
        return (float) plugin.getConfig().getDouble("sounds." + soundKey + ".pitch", 1.0);
    }

    /**
     * Kiểm tra xem sound có được bật không
     * @param soundKey Key của âm thanh
     * @return true nếu sound được bật
     */
    public boolean isSoundEnabled(String soundKey) {
        return plugin.getConfig().getBoolean("sounds." + soundKey + ".enabled", true);
    }

    /**
     * Play sound cho player từ config key
     * Tham khảo cách dùng từ HomeManager và TPAManager
     *
     * @param player Player để play sound
     * @param soundKey Key của sound trong config (vd: "home-set", "home-delete")
     */
    public void playSound(org.bukkit.entity.Player player, String soundKey) {
        // Kiểm tra xem sound có được bật không
        if (!isSoundEnabled(soundKey)) {
            return;
        }

        String soundName = getSoundName(soundKey);
        float volume = getSoundVolume(soundKey);
        float pitch = getSoundPitch(soundKey);

        // Convert sound name sang lowercase với underscore cho NamespacedKey
        // Ví dụ: "ENTITY_ENDERMAN_TELEPORT" -> "entity_enderman_teleport"
        String normalizedName = soundName.toLowerCase(); //.replace(" ", "_");

        // Sử dụng Registry.SOUNDS thay vì Sound.valueOf() (deprecated từ 1.21.3)
        Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(normalizedName));

        if (sound != null) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } else {
            // Sound không tồn tại, log warning và dùng fallback
            plugin.getLogger().warning("Sound không hợp lệ: " + soundName + " (key: " + soundKey + "), Lưu ý: sound_name phải có định dạng ví dụ như sau: entity.enderman.teleport");

            // Fallback: dùng UI_BUTTON_CLICK như HomeManager/TPAManager hay dùng
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, volume, pitch);
        }
    }

    /**
     * Play sound trực tiếp bằng Sound enum
     * Giống cách HomeManager và TPAManager sử dụng
     *
     * @param player Player để play sound
     * @param sound Sound enum trực tiếp
     * @param volume Volume (0.0 - 1.0)
     * @param pitch Pitch (0.5 - 2.0)
     */
    public void playSound(org.bukkit.entity.Player player, org.bukkit.Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
