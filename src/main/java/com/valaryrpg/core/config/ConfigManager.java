package com.valaryrpg.core.config;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Carica i YAML: config/storage in root, testi in lang/, suoni in sounds/, moduli in modules/.
 */
public final class ConfigManager {

    private final ValaryCorePlugin plugin;
    private YamlFile root;
    private YamlFile messages;
    private YamlFile vanilla;
    private YamlFile storage;
    private YamlFile sounds;
    private YamlFile worlds;
    private YamlFile homes;
    private YamlFile warps;
    private YamlFile players;
    private YamlFile moderation;
    private YamlFile mechanics;
    private YamlFile kits;
    private YamlFile prices;

    public ConfigManager(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        this.root = load("config.yml");
        this.storage = load("storage.yml");
        this.messages = load("lang/messages.yml");
        this.vanilla = load("lang/vanilla.yml");
        this.sounds = load("sounds/sounds.yml");
        this.worlds = load("modules/worlds.yml");
        this.homes = load("modules/homes.yml");
        this.warps = load("modules/warps.yml");
        this.players = load("modules/players.yml");
        this.moderation = load("modules/moderation.yml");
        this.mechanics = load("modules/mechanics.yml");
        this.kits = load("modules/kits.yml");
        this.prices = load("modules/prices.yml");
        migrateLegacySounds();
        migrateGroundItemHologram();
        migrateDisconnectScreens();
        this.plugin.getLogger().info("Configurazioni caricate.");
    }

    public FileConfiguration root() {
        return this.root.config();
    }

    public FileConfiguration messages() {
        return this.messages.config();
    }

    public FileConfiguration vanilla() {
        return this.vanilla.config();
    }

    public FileConfiguration storage() {
        return this.storage.config();
    }

    public FileConfiguration sounds() {
        return this.sounds.config();
    }

    public FileConfiguration worlds() {
        return this.worlds.config();
    }

    public FileConfiguration homes() {
        return this.homes.config();
    }

    public FileConfiguration warps() {
        return this.warps.config();
    }

    public FileConfiguration players() {
        return this.players.config();
    }

    public FileConfiguration moderation() {
        return this.moderation.config();
    }

    public FileConfiguration mechanics() {
        return this.mechanics.config();
    }

    public FileConfiguration kits() {
        return this.kits.config();
    }

    public FileConfiguration prices() {
        return this.prices.config();
    }

    public void saveWorlds() {
        this.worlds.save();
    }

    public void saveWarps() {
        this.warps.save();
    }

    public void saveMechanics() {
        this.mechanics.save();
    }

    private void migrateLegacySounds() {
        ConfigurationSection old = this.root.config().getConfigurationSection("sounds");
        if (old == null) {
            return;
        }
        boolean changed = false;
        for (String key : old.getKeys(false)) {
            if (this.sounds.config().contains(key)) {
                continue;
            }
            this.sounds.config().set(key + ".sound", old.getString(key));
            this.sounds.config().set(key + ".volume", 1.0);
            this.sounds.config().set(key + ".pitch", 1.0);
            changed = true;
        }
        if (changed) {
            this.sounds.save();
            this.plugin.getLogger().info("Suoni migrati da config.yml a sounds/sounds.yml");
        }
    }

    /**
     * Il primo hologram usava font uniform + box nero nametag: se i valori sono ancora quelli, passa allo stile senza riquadro.
     */
    private void migrateGroundItemHologram() {
        FileConfiguration yaml = this.mechanics.config();
        boolean changed = false;
        if (yaml.getInt("ground-items.background") == 1073741824) {
            yaml.set("ground-items.background", 0);
            changed = true;
        }
        if ("minecraft:uniform".equals(yaml.getString("ground-items.font"))) {
            yaml.set("ground-items.font", "");
            changed = true;
        }
        if (yaml.getBoolean("ground-items.see-through") && yaml.getInt("ground-items.background") == 0) {
            yaml.set("ground-items.see-through", false);
            changed = true;
        }
        if (yaml.getDouble("ground-items.scale") == 0.45D) {
            yaml.set("ground-items.scale", 0.8D);
            changed = true;
        }
        if ("<white>{name} <gray>x{amount}".equals(yaml.getString("ground-items.name-format"))) {
            yaml.set("ground-items.name-format", "{name} <dark_gray>×<white>{amount}");
            changed = true;
        }
        if (changed) {
            this.mechanics.save();
            this.plugin.getLogger().info("Hologram item a terra aggiornato (niente box nametag).");
        }
    }

    /**
     * Le schermate vecchie erano stringhe: il merge non le converte in liste.
     */
    private void migrateDisconnectScreens() {
        FileConfiguration yaml = this.messages.config();
        YamlConfiguration defaults = jarMessages();
        if (defaults == null) {
            return;
        }
        boolean changed = false;
        for (String key : new String[]{
                "moderation.kick-screen",
                "moderation.ban-screen",
                "moderation.ipban-screen"
        }) {
            if (yaml.isList(key)) {
                continue;
            }
            yaml.set(key, defaults.get(key));
            changed = true;
        }
        if ("{hours}h {minutes}m".equals(yaml.getString("duration.hours"))) {
            yaml.set("duration.hours", defaults.getString("duration.hours"));
            changed = true;
        }
        if ("{days}d {hours}h".equals(yaml.getString("duration.days"))) {
            yaml.set("duration.days", defaults.getString("duration.days"));
            changed = true;
        }
        if (changed) {
            this.messages.save();
            this.plugin.getLogger().info("Schermate kick/ban migrate in lang/messages.yml");
        }
    }

    private YamlConfiguration jarMessages() {
        try (InputStream input = this.plugin.getResource("lang/messages.yml")) {
            if (input == null) {
                return null;
            }
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return defaults;
        } catch (Exception exception) {
            return null;
        }
    }

    private YamlFile load(String fileName) {
        YamlFile yaml = new YamlFile(this.plugin, fileName);
        yaml.load();
        return yaml;
    }
}
