//made with love by nyxon

package com.modern.core.security;

import com.modern.core.ModernCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public final class DefaultRankBootstrap {

  private static final String RECLUTA_PREFIX =
      "<b><gradient:#C9D1DC:#8B8B9A>ʀᴇᴄʟᴜᴛᴀ</gradient></b> ";

  private static final List<String> PLAYER_GROUPS = List.of(
      "default",
      "kryndel",
      "vaelorn",
      "orynth",
      "nymira",
      "quirith",
      "eldryn",
      "volnaire",
      "iskara",
      "helper",
      "moderator",
      "admin"
  );

  private static final List<String> PLAYER_PERMS = List.of(
      "valary.tomb",
      "nyxonvaults.vault.1",
      "nyxonvaults.selector",
      "nyxonvaults.openremote",
      "valary.party",
      "valary.party.accept",
      "valary.trash",
      "valary.reciclatore",
      "nyxoncrates.open.*",
      "nyxoncrates.preview",

      "commandpanel.panel.default",

      "mmocore.attributes",

      "authme.player.changepassword",

      "valary.expeditions",

      "nyxonvaults.buy"
  );

  private final ModernCorePlugin plugin;

  public DefaultRankBootstrap(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  public void schedule() {
    Bukkit.getScheduler().runTaskLater(this.plugin, this::apply, 60L);
  }

  public void apply() {
    if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
      return;
    }
    ConfigurationSection ensure = this.plugin.configs().root().getConfigurationSection("ranks.ensure");
    if (ensure != null) {
      applyConfigured(ensure);
      return;
    }
    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp group default setdisplayname recluta");
    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp group default setweight 1");
    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp group default meta removeprefix 1");
    Bukkit.dispatchCommand(
        Bukkit.getConsoleSender(),
        "lp group default meta setprefix 10 \"" + RECLUTA_PREFIX + "\""
    );
    for (String group : PLAYER_GROUPS) {
      for (String perm : PLAYER_PERMS) {
        dispatch("lp group " + group + " permission set " + perm + " true");
      }
    }
  }

  private void applyConfigured(ConfigurationSection ensure) {
    List<String> commands = new ArrayList<>();
    for (String group : ensure.getKeys(false)) {
      ConfigurationSection section = ensure.getConfigurationSection(group);
      if (section == null) {
        continue;
      }
      commands.add("lp creategroup " + group);
      String parent = section.getString("parent", "");
      if (!parent.isBlank()) {
        commands.add("lp group " + group + " parent add " + parent);
      }
      int weight = section.getInt("weight", 0);
      if (weight > 0) {
        commands.add("lp group " + group + " setweight " + weight);
      }
      String prefix = section.getString("prefix", "");
      if (!prefix.isBlank()) {
        commands.add("lp group " + group + " meta setprefix 100 \"" + prefix + "\"");
      }
      for (String perm : section.getStringList("permissions")) {
        commands.add("lp group " + group + " permission set " + perm + " true");
      }
    }
    long delay = 0L;
    for (String command : commands) {
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.dispatch(command), delay);
      delay += 15L;
    }
  }

  private void dispatch(String command) {
    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
  }
}
