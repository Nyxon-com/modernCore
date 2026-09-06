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

    public ConfigManager(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        this.root = load("config.yml");
        this.messages = load("messages.yml");
        this.storage = load("storage.yml");
        this.worlds = load("worlds.yml");
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

    public void saveWorlds() {
        this.worlds.save();
    }

    private YamlFile load(String fileName) {
        YamlFile yaml = new YamlFile(this.plugin, fileName);
        yaml.load();
        return yaml;
    }
}
