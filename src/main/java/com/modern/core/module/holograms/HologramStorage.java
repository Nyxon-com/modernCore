//made with love by nyxon

package com.modern.core.module.holograms;

import com.modern.core.ModernCorePlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

final class HologramStorage {

  private final ModernCorePlugin plugin;

  HologramStorage(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  File folder() {
    return new File(this.plugin.getDataFolder(), settings().getString("folder", "holo"));
  }

  void initialize() {
    File folder = folder();
    if (!folder.exists() && !folder.mkdirs()) {
      throw new IllegalStateException("Impossibile creare la cartella hologram: " + folder.getAbsolutePath());
    }
    copyExample(folder);
  }

  Map<String, YamlConfiguration> loadAll() {
    File folder = folder();
    if (!folder.exists() && !folder.mkdirs()) {
      throw new IllegalStateException("Impossibile creare la cartella hologram: " + folder.getAbsolutePath());
    }
    Map<String, YamlConfiguration> loaded = new LinkedHashMap<>();
    File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
    if (files == null) {
      throw new IllegalStateException("Impossibile leggere la cartella hologram: " + folder.getAbsolutePath());
    }
    Arrays.sort(files, java.util.Comparator.comparing(File::getName));
    for (File file : files) {
      String id = idOf(file.getName());
      if (id.isBlank() || id.equals("holograms")) {
        continue;
      }
      try {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        loaded.put(id, yaml);
      } catch (Exception exception) {
        throw new IllegalStateException("Hologram YAML non valido: " + file.getAbsolutePath(), exception);
      }
    }
    return loaded;
  }

  void save(String id, FileConfiguration yaml) {
    File file = fileOf(id);
    File parent = file.getParentFile();
    if (parent != null && !parent.exists()) {
      parent.mkdirs();
    }
    try {
      yaml.save(file);
    } catch (Exception exception) {
      this.plugin.getLogger().log(Level.SEVERE, "Impossibile salvare hologram " + id, exception);
    }
  }

  boolean delete(String id) {
    File file = fileOf(id);
    return !file.exists() || file.delete();
  }

  Set<String> names(Map<String, YamlConfiguration> loaded) {
    return loaded.keySet();
  }

  int importFancy(File source) {
    if (source == null || !source.isFile()) {
      return 0;
    }
    YamlConfiguration dump = new YamlConfiguration();
    try {
      dump.load(source);
    } catch (Exception exception) {
      this.plugin.getLogger().log(Level.WARNING, "Import hologram fallito: " + source.getName(), exception);
      return 0;
    }
    ConfigurationSection holos = dump.getConfigurationSection("holograms");
    if (holos == null) {
      return 0;
    }
    int created = 0;
    for (String raw : holos.getKeys(false)) {
      String id = normalize(raw);
      if (id.isBlank()) {
        continue;
      }
      File target = fileOf(id);
      if (target.exists()) {
        continue;
      }
      ConfigurationSection section = holos.getConfigurationSection(raw);
      if (section == null) {
        continue;
      }
      YamlConfiguration yaml = new YamlConfiguration();
      for (String key : section.getKeys(true)) {
        if (!section.isConfigurationSection(key)) {
          yaml.set(key, section.get(key));
        }
      }
      if (!yaml.contains("enabled")) {
        yaml.set("enabled", true);
      }
      save(id, yaml);
      created++;
    }
    File backup = new File(source.getParentFile(), source.getName() + ".imported");
    if (!source.renameTo(backup)) {
      this.plugin.getLogger().warning("Hologram importati, ma " + source.getName() + " non è stato rinominato.");
    }
    if (created > 0) {
      this.plugin.getLogger().info("Importati " + created + " hologram da " + source.getName() + ".");
    }
    return created;
  }

  static String normalize(String name) {
    return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
  }

  private void copyExample(File folder) {
    File[] existing = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
    if (existing != null && existing.length > 0) {
      return;
    }
    this.plugin.saveResource("holo/example.yml", false);
  }

  private File fileOf(String id) {
    return new File(folder(), normalize(id) + ".yml");
  }

  private static String idOf(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return normalize(dot < 0 ? fileName : fileName.substring(0, dot));
  }

  private FileConfiguration settings() {
    return this.plugin.configs().holograms();
  }
}
