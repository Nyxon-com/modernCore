//made with love by nyxon

package com.modern.core.command;

import com.modern.core.ModernCorePlugin;
import com.modern.core.config.YamlFile;
import com.modern.core.util.Perms;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class CommandFilter {

  private final ModernCorePlugin plugin;
  private boolean enabled = true;
  private boolean opsSeeAll = true;
  private long cooldownMillis = 800L;
  private long panelCooldownMillis = 1500L;
  private final Set<String> visible = new HashSet<>();
  private final Set<String> disabled = new HashSet<>();
  private final Set<String> disabledPrefixes = new HashSet<>();
  private final Set<String> cooldownExempt = new HashSet<>();
  private final Set<String> panelCommands = new HashSet<>();
  private final List<Unlock> unlocks = new ArrayList<>();
  private final Map<UUID, Long> nextCommandAt = new ConcurrentHashMap<>();
  private final Map<UUID, Long> nextPanelAt = new ConcurrentHashMap<>();

  CommandFilter(ModernCorePlugin plugin) {
    this.plugin = plugin;
    reload();
  }

  void reload() {
    YamlFile file = new YamlFile(this.plugin, "modules/command-filter.yml");
    file.load();
    FileConfiguration yaml = file.config();
    this.enabled = yaml.getBoolean("enabled", true);
    this.opsSeeAll = yaml.getBoolean("ops-see-all", true);
    this.cooldownMillis = Math.max(0L, yaml.getLong("cooldown-millis", 800L));
    this.panelCooldownMillis = Math.max(0L, yaml.getLong("panel-cooldown-millis", 1500L));
    this.visible.clear();
    this.unlocks.clear();
    this.disabled.clear();
    this.disabledPrefixes.clear();
    this.cooldownExempt.clear();
    this.panelCommands.clear();
    for (String name : yaml.getStringList("visible")) {
      this.visible.add(normalize(name));
    }
    for (String name : yaml.getStringList("disabled")) {
      this.disabled.add(normalize(name));
    }
    for (String name : yaml.getStringList("hidden")) {
      this.disabled.add(normalize(name));
    }
    for (String prefix : yaml.getStringList("disabled-prefixes")) {
      String value = normalize(prefix);
      if (!value.isEmpty()) {
        this.disabledPrefixes.add(value);
      }
    }
    for (String prefix : yaml.getStringList("hidden-prefixes")) {
      String value = normalize(prefix);
      if (!value.isEmpty()) {
        this.disabledPrefixes.add(value);
      }
    }
    List<String> exempt = yaml.getStringList("cooldown-exempt");
    if (exempt.isEmpty()) {
      this.cooldownExempt.addAll(List.of("login", "l", "log", "register", "reg", "email", "captcha", "2fa", "totp", "changepassword"));
    } else {
      for (String name : exempt) {
        this.cooldownExempt.add(normalize(name));
      }
    }
    List<String> panels = yaml.getStringList("panel-commands");
    if (panels.isEmpty()) {
      this.panelCommands.addAll(List.of("cp", "commandpanel", "commandpanels", "panel"));
    } else {
      for (String name : panels) {
        this.panelCommands.add(normalize(name));
      }
    }
    loadRanks(yaml.getConfigurationSection("vip"), "group.");
    loadRanks(yaml.getConfigurationSection("staff"), "group.");
    this.visible.removeAll(this.disabled);
    this.plugin.getLogger().info("Command filter: " + this.visible.size() + " visibili, "
        + this.disabled.size() + " disabilitati, " + this.unlocks.size() + " rank VIP, cooldown "
        + this.cooldownMillis + "ms / panel " + this.panelCooldownMillis + "ms.");
  }

  double tryCommand(org.bukkit.entity.Player player, String label) {
    if (player == null || seeAll(player) || Perms.has(this.plugin, player, "command-cooldown-bypass")) {
      return 0.0D;
    }
    String name = normalize(label);
    if (name.isEmpty() || this.cooldownExempt.contains(name)) {
      return 0.0D;
    }
    long now = System.currentTimeMillis();
    boolean panel = this.panelCommands.contains(name);
    Map<UUID, Long> map = panel ? this.nextPanelAt : this.nextCommandAt;
    long wait = panel ? this.panelCooldownMillis : this.cooldownMillis;
    if (wait <= 0L) {
      return 0.0D;
    }
    Long until = map.get(player.getUniqueId());
    if (until != null && until > now) {
      return (until - now) / 1000.0D;
    }
    map.put(player.getUniqueId(), now + wait);
    return 0.0D;
  }

  private void loadRanks(ConfigurationSection section, String permissionPrefix) {
    if (section == null) {
      return;
    }
    for (String rank : section.getKeys(false)) {
      ConfigurationSection extra = section.getConfigurationSection(rank);
      Set<String> commands = new HashSet<>();
      Set<String> prefixes = new HashSet<>();
      if (extra == null) {
        for (String name : section.getStringList(rank)) {
          commands.add(normalize(name));
        }
      } else {
        for (String name : extra.getStringList("commands")) {
          commands.add(normalize(name));
        }
        for (String prefix : extra.getStringList("prefixes")) {
          String value = normalize(prefix);
          if (!value.isEmpty()) {
            prefixes.add(value);
          }
        }
      }
      if (!commands.isEmpty() || !prefixes.isEmpty()) {
        this.unlocks.add(new Unlock(permissionPrefix + rank.toLowerCase(Locale.ROOT), commands, prefixes));
      }
    }
  }

  boolean seeAll(CommandSender sender) {
    if (!this.opsSeeAll) {
      return false;
    }
    return sender.isOp() || Perms.has(this.plugin, sender, "command-see-all");
  }

  boolean allowed(CommandSender sender, String rawName, Command command) {
    if (!this.enabled) {
      return true;
    }
    if (seeAll(sender)) {
      return true;
    }
    return playerVisible(sender, rawName, command);
  }

  boolean playerVisible(CommandSender sender, String rawName, Command command) {
    String name = normalize(rawName);
    if (name.isEmpty()) {
      return false;
    }
    if (rawName != null && rawName.indexOf(':') >= 0) {
      return false;
    }

    if (isWorldEditSlashCommand(rawName)) {
      return false;
    }
    if (blocked(name, rawName, command)) {
      return false;
    }
    if (unlocked(sender, name)) {
      return true;
    }
    if (!this.visible.contains(name)) {
      return false;
    }
    return pluginAllowed(sender, command);
  }

  private boolean blocked(String name, String rawName, Command command) {
    String raw = rawName == null ? "" : rawName.trim().toLowerCase(Locale.ROOT);
    if (this.disabled.contains(name) || this.disabled.contains(raw)) {
      return true;
    }
    String owner = owningPlugin(command);
    if (owner != null && (this.disabled.contains(owner) || this.disabledPrefixes.contains(owner))) {
      return !this.visible.contains(name);
    }
    for (String prefix : this.disabledPrefixes) {
      if (matchesPrefix(name, prefix) || matchesPrefix(raw, prefix)) {
        return !this.visible.contains(name);
      }
    }
    return false;
  }

  private static boolean matchesPrefix(String value, String prefix) {
    return value.equals(prefix) || value.startsWith(prefix);
  }

  private static String owningPlugin(Command command) {
    if (command instanceof PluginIdentifiableCommand identifiable) {
      return identifiable.getPlugin().getName().toLowerCase(Locale.ROOT);
    }
    return null;
  }

  private boolean unlocked(CommandSender sender, String name) {
    for (Unlock unlock : this.unlocks) {
      if (!sender.hasPermission(unlock.permission)) {
        continue;
      }
      if (unlock.commands.contains(name)) {
        return true;
      }
      for (String prefix : unlock.prefixes) {
        if (name.equals(prefix) || name.startsWith(prefix)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean pluginAllowed(CommandSender sender, Command command) {
    if (command == null) {
      return true;
    }
    String permission = command.getPermission();
    if (permission == null || permission.isBlank()) {
      return true;
    }
    return command.testPermissionSilent(sender);
  }

  static boolean isWorldEditSlashCommand(String rawName) {
    if (rawName == null || rawName.isBlank()) {
      return false;
    }
    String raw = rawName.trim();
    int colon = raw.indexOf(':');
    if (colon >= 0) {
      raw = raw.substring(colon + 1);
    }
    return raw.startsWith("/");
  }

  static String normalize(String raw) {
    if (raw == null) {
      return "";
    }
    String name = raw.trim().toLowerCase(Locale.ROOT);
    int colon = name.indexOf(':');
    if (colon >= 0) {
      name = name.substring(colon + 1);
    }
    while (name.startsWith("/")) {
      name = name.substring(1);
    }
    return name;
  }

  private record Unlock(String permission, Set<String> commands, Set<String> prefixes) {
  }
}
