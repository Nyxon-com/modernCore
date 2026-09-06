package com.valaryrpg.core.config;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Carica e valida tutti i YAML del plugin.
 */
public final class ConfigManager {

    private final ValaryCorePlugin plugin;
    private YamlFile root;
    private YamlFile messages;
    private YamlFile storage;
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
        this.messages = load("messages.yml");
        this.storage = load("storage.yml");
        this.worlds = load("worlds.yml");
        this.homes = load("homes.yml");
        this.warps = load("warps.yml");
        this.players = load("players.yml");
        this.moderation = load("moderation.yml");
        this.mechanics = load("mechanics.yml");
        this.kits = load("kits.yml");
        this.prices = load("prices.yml");
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

    private YamlFile load(String fileName) {
        YamlFile yaml = new YamlFile(this.plugin, fileName);
        yaml.load();
        return yaml;
    }
}
