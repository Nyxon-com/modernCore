package com.valaryrpg.core.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * YAML su disco con default dal JAR: se manca o è corrotto viene rigenerato,
 * se mancano solo alcune chiavi vengono aggiunte senza sovrascrivere i valori utente.
 */
public final class YamlFile {

    private final JavaPlugin plugin;
    private final String fileName;
    private File file;
    private FileConfiguration config;

    public YamlFile(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
    }

    public void load() {
        this.plugin.getDataFolder().mkdirs();
        this.file = new File(this.plugin.getDataFolder(), this.fileName);

        if (!this.file.exists()) {
            this.plugin.saveResource(this.fileName, false);
        }

        try {
            YamlConfiguration loaded = new YamlConfiguration();
            loaded.load(this.file);
            this.config = loaded;
        } catch (Exception exception) {
            File backup = new File(
                    this.plugin.getDataFolder(),
                    this.fileName + ".broken." + System.currentTimeMillis() + ".yml"
            );
            if (this.file.renameTo(backup)) {
                this.plugin.getLogger().warning("File " + this.fileName + " corrotto, backup: " + backup.getName());
            } else {
                this.plugin.getLogger().warning("File " + this.fileName + " corrotto e backup non riuscito.");
            }
            this.plugin.saveResource(this.fileName, true);
            this.config = YamlConfiguration.loadConfiguration(this.file);
            this.plugin.getLogger().log(Level.WARNING, "Rigenerato " + this.fileName, exception);
        }

        mergeMissingKeys();
    }

    public void save() {
        try {
            this.config.save(this.file);
        } catch (Exception exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Impossibile salvare " + this.fileName, exception);
        }
    }

    public FileConfiguration config() {
        return this.config;
    }

    private void mergeMissingKeys() {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream input = this.plugin.getResource(this.fileName)) {
            if (input == null) {
                return;
            }
            defaults.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            this.plugin.getLogger().log(Level.WARNING, "Default JAR non letti per " + this.fileName, exception);
            return;
        }

        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) {
                continue;
            }
            if (!this.config.contains(key)) {
                this.config.set(key, defaults.get(key));
                changed = true;
            }
        }

        if (changed) {
            save();
            this.plugin.getLogger().info("Chiavi mancanti aggiunte in " + this.fileName);
        }
    }
}
