//made with love by nyxon

package com.modern.core.module.features;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.modern.core.ModernCorePlugin;
import com.modern.core.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

public final class SkinService {

  private final ModernCorePlugin plugin;

  public SkinService(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  public void apply(Player player, String account) {
    String name = account.trim();
    if (!name.matches("[A-Za-z0-9_]{1,16}")) {
      this.plugin.messages().send(player, "features.skin-invalid", Map.of("skin", name));
      return;
    }
    PlayerProfile lookup = Bukkit.createProfile(name);
    lookup.update().whenComplete((updated, error) -> Tasks.sync(this.plugin, () -> finish(player, name, updated, error)));
  }

  public void clear(Player player) {
    PlayerProfile own = player.getPlayerProfile();
    own.removeProperty("textures");
    player.setPlayerProfile(own);
    this.plugin.messages().send(player, "features.skin-cleared");
  }

  private void finish(Player player, String name, PlayerProfile updated, Throwable error) {
    if (!player.isOnline()) {
      return;
    }
    if (error != null || updated == null) {
      this.plugin.messages().send(player, "features.skin-missing", Map.of("skin", name));
      return;
    }
    ProfileProperty textures = null;
    for (ProfileProperty property : updated.getProperties()) {
      if ("textures".equals(property.getName())) {
        textures = property;
      }
    }
    if (textures == null) {
      this.plugin.messages().send(player, "features.skin-missing", Map.of("skin", name));
      return;
    }
    PlayerProfile own = player.getPlayerProfile();
    own.removeProperty("textures");
    own.setProperty(textures);
    player.setPlayerProfile(own);
    this.plugin.messages().send(player, "features.skin-set", Map.of("skin", name.toLowerCase(Locale.ROOT)));
  }
}
