package com.modern.core.module.moderation;

import com.modern.core.ModernCorePlugin;
import com.modern.core.gui.GuiLayouts;
import com.modern.core.gui.Panels;
import com.modern.core.module.players.PlayerDataRepository;
import com.modern.core.module.players.PlayersModule;
import com.modern.core.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VanishService {

  public static final String MENU = "vanishedit";

  private final ModernCorePlugin plugin;
  private final PlayerDataRepository players;
  private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
  private final Set<UUID> nightVision = ConcurrentHashMap.newKeySet();
  private final Set<UUID> noPickup = ConcurrentHashMap.newKeySet();

  public VanishService(ModernCorePlugin plugin, PlayerDataRepository players) {
    this.plugin = plugin;
    this.players = players;
  }

  public void restore(Player player) {
    UUID uuid = player.getUniqueId();
    if (this.vanished.contains(uuid)) {
      apply(player, true, false);
      Tasks.later(this.plugin, 2L, () -> {
        if (player.isOnline() && this.vanished.contains(uuid)) {
          apply(player, true, false);
        }
      });
      return;
    }
    Tasks.async(this.plugin, () -> {
      boolean stored = this.players.vanished(uuid);
      if (!stored) {
        return;
      }
      Tasks.sync(this.plugin, () -> {
        if (!player.isOnline()) {
          return;
        }
        apply(player, true, false);
      });
      Tasks.later(this.plugin, 2L, () -> {
        if (player.isOnline() && this.vanished.contains(uuid)) {
          apply(player, true, false);
        }
      });
    });
  }

  public boolean isVanished(Player player) {
    return this.vanished.contains(player.getUniqueId());
  }

  public void reapply(Player player) {
    if (player.isOnline() && this.vanished.contains(player.getUniqueId())) {
      apply(player, true, false);
    }
  }

  public boolean noPickup(Player player) {
    return this.noPickup.contains(player.getUniqueId());
  }

  public void toggle(Player target, CommandSender initiator) {
    set(target, !isVanished(target), initiator);
  }

  public void set(Player target, boolean enabled, CommandSender initiator) {
    apply(target, enabled, true);
    UUID uuid = target.getUniqueId();
    Tasks.async(this.plugin, () -> this.players.updateVanish(uuid, enabled));
    boolean self = initiator instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
    this.plugin.messages().send(initiator, enabled ? "vanish.enabled-for" : "vanish.disabled-for", Map.of("player", target.getName()));
    if (!self) {
      this.plugin.messages().send(target, enabled ? "vanish.enabled-by" : "vanish.disabled-by", Map.of("player", initiator.getName()));
    }
  }

  public void handleJoin(Player joining) {
    for (UUID uuid : Set.copyOf(this.vanished)) {
      Player vanishedPlayer = Bukkit.getPlayer(uuid);
      if (vanishedPlayer == null || vanishedPlayer.equals(joining)) {
        continue;
      }
      if (!canSeeVanished(joining)) {
        joining.hidePlayer(this.plugin, vanishedPlayer);
      }
    }
    if (isVanished(joining)) {
      hideFromAll(joining);
    }
  }

  public boolean canSeeVanished(Player viewer) {
    return viewer.hasPermission(this.plugin.configs().root().getString("permissions.vanish-see", "valary.vanish.see"));
  }

  public void toggleNightVision(Player player) {
    if (this.nightVision.remove(player.getUniqueId())) {
      player.removePotionEffect(PotionEffectType.NIGHT_VISION);
      this.plugin.messages().send(player, "vanish.edit-off", Map.of("flag", "nightvision"));
      return;
    }
    this.nightVision.add(player.getUniqueId());
    player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, true, false, false));
    this.plugin.messages().send(player, "vanish.edit-on", Map.of("flag", "nightvision"));
  }

  public void openEdit(Player player) {
    Panels.open(this.plugin, player, MENU, this.plugin.configs().moderation().getConfigurationSection("vanishedit.gui"), Map.of());
  }

  public void clickEdit(Player player, int slot) {
    ConfigurationSection button = GuiLayouts.button(this.plugin.configs().moderation().getConfigurationSection("vanishedit.gui"), slot);
    String action = GuiLayouts.action(button);
    if (action.equals("nightvision") || action.equals("nv")) {
      toggleNightVision(player);
      openEdit(player);
      return;
    }
    if (action.equals("pickup") || action.equals("nopickup")) {
      toggleNoPickup(player);
      openEdit(player);
    }
  }

  public void toggleNoPickup(Player player) {
    if (!this.noPickup.add(player.getUniqueId())) {
      this.noPickup.remove(player.getUniqueId());
      this.plugin.messages().send(player, "vanish.edit-off", Map.of("flag", "pickup"));
      return;
    }
    this.plugin.messages().send(player, "vanish.edit-on", Map.of("flag", "pickup"));
  }

  public void unload(UUID uuid) {
    this.nightVision.remove(uuid);
    this.noPickup.remove(uuid);
  }

  private void apply(Player target, boolean enabled, boolean announce) {
    if (enabled) {
      this.vanished.add(target.getUniqueId());
      hideFromAll(target);
      target.setInvisible(true);
      if (this.plugin.configs().moderation().getBoolean("vanish.night-vision", true)) {
        this.nightVision.add(target.getUniqueId());
        target.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, true, false, false));
      }
      if (this.plugin.configs().moderation().getBoolean("vanish.no-pickup", true)) {
        this.noPickup.add(target.getUniqueId());
      }
      if (announce) {
        fakeBroadcast(target, true);
      }
    } else {
      this.vanished.remove(target.getUniqueId());
      this.nightVision.remove(target.getUniqueId());
      this.noPickup.remove(target.getUniqueId());
      target.setInvisible(false);
      target.removePotionEffect(PotionEffectType.NIGHT_VISION);
      showToAll(target);
      PlayersModule players = this.plugin.modules().get("players");
      if (players != null && players.nicks() != null) {
        players.nicks().reapply(target);
      }
      if (announce) {
        fakeBroadcast(target, false);
      }
    }
  }

  private void fakeBroadcast(Player target, boolean leaving) {
    if (!this.plugin.configs().moderation().getBoolean("vanish.fake-join-quit", true)) {
      return;
    }
    String display = displayName(target);
    net.kyori.adventure.text.Component message = this.plugin.messages().component(
        leaving ? "join-quit.quit" : "join-quit.join",
        Map.of(
            "player", this.plugin.messages().escape(target.getName()),
            "displayname", display
        )
    );
    for (Player other : Bukkit.getOnlinePlayers()) {
      if (other.equals(target) || canSeeVanished(other)) {
        continue;
      }
      other.sendMessage(message);
    }
  }

  private String displayName(Player player) {
    PlayersModule players = this.plugin.modules().get("players");
    if (players != null && players.nicks() != null) {
      return players.nicks().displayMini(player);
    }
    return this.plugin.messages().escape(player.getName());
  }

  private void hideFromAll(Player vanishedPlayer) {
    for (Player other : Bukkit.getOnlinePlayers()) {
      if (other.equals(vanishedPlayer) || canSeeVanished(other)) {
        continue;
      }
      other.hidePlayer(this.plugin, vanishedPlayer);
    }
  }

  private void showToAll(Player player) {
    for (Player other : Bukkit.getOnlinePlayers()) {
      other.showPlayer(this.plugin, player);
    }
  }
}
