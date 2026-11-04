package com.modern.core.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.logging.Level;

public final class YamlFile {

  private static final Map<String, String> LEGACY = Map.ofEntries(
      Map.entry("lang/messages.yml", "messages.yml"),
      Map.entry("lang/vanilla.yml", "vanilla.yml"),
      Map.entry("modules/worlds.yml", "worlds.yml"),
      Map.entry("modules/homes.yml", "homes.yml"),
      Map.entry("modules/warps.yml", "warps.yml"),
      Map.entry("modules/players.yml", "players.yml"),
      Map.entry("modules/moderation.yml", "moderation.yml"),
      Map.entry("modules/mechanics.yml", "mechanics.yml"),
      Map.entry("modules/kits.yml", "kits.yml"),
      Map.entry("modules/prices.yml", "prices.yml")
  );

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
    File parent = this.file.getParentFile();
    if (parent != null) {
      parent.mkdirs();
    }

    if (!this.file.exists()) {
      migrateLegacy();
    }
    if (!this.file.exists()) {
      this.plugin.saveResource(this.fileName, false);
    }

    try {
      YamlConfiguration loaded = new YamlConfiguration();
      loaded.load(this.file);
      this.config = loaded;
    } catch (Exception exception) {
      File backup = new File(
          this.file.getParentFile(),
          this.file.getName() + ".broken." + System.currentTimeMillis() + ".yml"
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

  public void loadStrict() throws Exception {
    this.file = new File(this.plugin.getDataFolder(), this.fileName);
    if (!this.file.isFile()) {
      throw new IllegalStateException("File configurazione mancante: " + this.file.getAbsolutePath());
    }
    this.config = readStrict(this.file);
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

  public File file() {
    return this.file;
  }

  static YamlConfiguration readStrict(File file) throws Exception {
    YamlConfiguration loaded = new YamlConfiguration();
    loaded.load(file);
    return loaded;
  }

  private void migrateLegacy() {
    String legacyName = LEGACY.get(this.fileName);
    if (legacyName == null) {
      return;
    }
    File legacy = new File(this.plugin.getDataFolder(), legacyName);
    if (!legacy.exists()) {
      return;
    }
    try {
      Files.move(legacy.toPath(), this.file.toPath());
      this.plugin.getLogger().info("Spostato " + legacyName + " → " + this.fileName);
    } catch (Exception exception) {
      this.plugin.getLogger().log(Level.WARNING, "Impossibile migrare " + legacyName, exception);
    }
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
