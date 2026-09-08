package com.valaryrpg.core.config;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Carica i YAML: config/storage in root, testi in lang/, suoni in sounds/, moduli in modules/.
 */
public final class ConfigManager {

    private final ValaryCorePlugin plugin;
    private YamlFile root;
    private YamlFile messages;
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
        this.plugin.getLogger().info("Configurazioni caricate.");
    }

    public FileConfiguration root() {
        return this.root.config();
    }

    public FileConfiguration messages() {
        return this.messages.config();
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

    private YamlFile load(String fileName) {
        YamlFile yaml = new YamlFile(this.plugin, fileName);
        yaml.load();
        return yaml;
    }
}
