//made with love by nyxon

package com.modern.core.module.players;

import com.modern.core.ModernCorePlugin;
import com.modern.core.module.chat.ChatModule;
import com.modern.core.module.moderation.ModerationModule;
import com.modern.core.util.ItemStacks;
import com.modern.core.util.Tasks;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PlayersListener implements Listener {

  private final ModernCorePlugin plugin;
  private final PlayerDataRepository repository;
  private final NickService nicks;
  private final FlyService fly;
  private final GodService god;
  private final GameModeService gamemodes;
  private final PrivateMessageService messages;
  private final PlaytimeService playtime;

  public PlayersListener(
      ModernCorePlugin plugin,
      PlayerDataRepository repository,
      NickService nicks,
      FlyService fly,
      GodService god,
      GameModeService gamemodes,
      PrivateMessageService messages,
      PlaytimeService playtime
  ) {
    this.plugin = plugin;
    this.repository = repository;
    this.nicks = nicks;
    this.fly = fly;
    this.god = god;
    this.gamemodes = gamemodes;
    this.messages = messages;
    this.playtime = playtime;
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    UUID uuid = player.getUniqueId();
    String name = player.getName();
    String ip = ipOf(player);
    long now = System.currentTimeMillis();
    event.joinMessage(null);
    Tasks.async(this.plugin, () -> {
      Optional<PlayerProfile> profile = Optional.empty();
      boolean godMode = false;
      try {
        this.repository.touchJoin(uuid, name, ip, now);
        profile = this.repository.find(uuid);
        godMode = this.repository.god(uuid);
      } catch (Exception exception) {
        this.plugin.getLogger().warning("Join SQL fallito per " + name + ": " + exception.getMessage());
      }
      Optional<PlayerProfile> loaded = profile;
      boolean restoreGod = godMode;
      Tasks.later(this.plugin, 2L, () -> {
        restore(player, loaded, restoreGod);
        applyOfflineInventory(player);
      });
    });
    this.playtime.startSession(uuid);
    restoreDeathBack(player);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoinAnnounce(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    boolean first = !player.hasPlayedBefore();
    Tasks.later(this.plugin, 2L, () -> {
      if (!player.isOnline()) {
        return;
      }
      if (bannedHold(player)) {
        return;
      }
      announce(player, first ? "join-quit.first-join" : "join-quit.join");
    });
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    UUID uuid = player.getUniqueId();
    if (!bannedHold(player)) {
      this.gamemodes.remember(player);
    }
    long session = this.playtime.endSession(uuid);
    long now = System.currentTimeMillis();
    String inventory = ItemStacks.encodePlayer(player.getInventory());
    Tasks.async(this.plugin, () -> {
      try {
        this.repository.addPlaytime(uuid, session, now);
        this.repository.saveInventory(uuid, inventory, false);
      } catch (Exception exception) {
        this.plugin.getLogger().warning("Quit SQL fallito per " + player.getName() + ": " + exception.getMessage());
      }
    });
    ChatModule chat = this.plugin.modules() == null ? null : this.plugin.modules().get("chat");
    if (chat != null && chat.chat() != null) {
      chat.chat().clearFlood(uuid);
    }
    this.messages.unload(uuid);
    this.god.unload(uuid);
    this.fly.unload(uuid);
    this.nicks.unload(uuid);
    this.gamemodes.unload(uuid);
    this.plugin.lastLocations().clear(uuid);
    event.quitMessage(null);
    if (!bannedHold(player)) {
      announce(player, "join-quit.quit");
    }
  }

  private void applyOfflineInventory(Player player) {
    if (!player.isOnline()) {
      return;
    }
    UUID uuid = player.getUniqueId();
    Tasks.async(this.plugin, () -> {
      try {
        if (!this.repository.inventoryDirty(uuid)) {
          return;
        }
        Optional<String> raw = this.repository.inventory(uuid);
        if (raw.isEmpty()) {
          this.repository.clearInventoryDirty(uuid);
          return;
        }
        ItemStack[] packed = ItemStacks.decode(raw.get());
        Tasks.sync(this.plugin, () -> {
          if (!player.isOnline()) {
            return;
          }
          ItemStacks.restorePlayer(player.getInventory(), packed);
          Tasks.async(this.plugin, () -> this.repository.clearInventoryDirty(uuid));
        });
      } catch (Exception exception) {
        this.plugin.getLogger().warning("Restore inventario offline fallito per " + player.getName() + ": " + exception.getMessage());
      }
    });
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onChat(AsyncChatEvent event) {
    this.nicks.formatChat(event);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onGameMode(PlayerGameModeChangeEvent event) {
    Player player = event.getPlayer();
    Tasks.nextTick(this.plugin, () -> {
      if (!player.isOnline() || bannedHold(player)) {
        return;
      }
      this.gamemodes.remember(player);
      this.fly.reapply(player);
      this.nicks.reapply(player);
    });
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onWorldTeleport(PlayerTeleportEvent event) {
    if (event.getFrom().getWorld() == null || event.getTo() == null || event.getTo().getWorld() == null) {
      return;
    }
    if (event.getFrom().getWorld().equals(event.getTo().getWorld())) {
      return;
    }
    this.gamemodes.followWorld(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDeath(PlayerDeathEvent event) {
    this.gamemodes.pauseRespawn(event.getEntity());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onRespawn(PlayerRespawnEvent event) {
    Player player = event.getPlayer();
    Tasks.nextTick(this.plugin, () -> {
      if (!player.isOnline() || bannedHold(player)) {
        return;
      }
      this.gamemodes.restoreAfterRespawn(player);
      this.fly.restoreAfterRespawn(player);
    });
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onWorldChange(PlayerChangedWorldEvent event) {
    Player player = event.getPlayer();
    Tasks.nextTick(this.plugin, () -> {
      if (!player.isOnline() || bannedHold(player)) {
        return;
      }
      this.fly.reapply(player);
      this.nicks.reapply(player);
      this.gamemodes.reapply(player);
      ModerationModule moderation = this.plugin.modules().get("moderation");
      if (moderation != null && moderation.vanish() != null) {
        moderation.vanish().reapply(player);
      }
    });
  }

  private void restore(Player player, Optional<PlayerProfile> profile, boolean godMode) {
    if (!player.isOnline() || bannedHold(player)) {
      return;
    }
    try {
      this.nicks.applyStored(player, profile.map(PlayerProfile::nick).orElse(null));
      this.fly.restore(player, profile.map(PlayerProfile::fly).orElse(false));
      this.gamemodes.restore(player, profile.map(PlayerProfile::gamemode).orElse(null));
      this.fly.reapply(player);
      this.god.restore(player, godMode);
      this.messages.loadSpy(player, profile.map(PlayerProfile::socialSpy).orElse(false));
    } catch (Exception exception) {
      this.plugin.getLogger().warning("Restore player fallito per " + player.getName() + ": " + exception.getMessage());
    }
  }

  private void announce(Player player, String key) {
    if (!this.plugin.configs().players().getBoolean("join-quit.enabled", true)) {
      return;
    }
    if (silentVanished(player)) {
      return;
    }
    if (!this.plugin.configs().messages().isSet(key)) {
      this.plugin.getLogger().warning("Manca " + key + " in lang/messages.yml");
      return;
    }
    String raw = this.plugin.configs().messages().getString(key);
    if (raw == null || raw.isBlank()) {
      return;
    }
    Component message = this.plugin.messages().component(key, Map.of(
        "player", this.plugin.messages().escape(player.getName()),
        "displayname", displayName(player)
    ));
    Bukkit.getServer().sendMessage(message);
  }

  private String displayName(Player player) {
    try {
      return MiniMessage.miniMessage().serialize(this.nicks.displayComponent(player));
    } catch (Exception exception) {
      return this.plugin.messages().escape(player.getName());
    }
  }

  private boolean silentVanished(Player player) {
    if (bannedHold(player)) {
      return true;
    }
    if (!this.plugin.configs().moderation().getBoolean("vanish.silent-join", true)) {
      return false;
    }
    ModerationModule moderation = this.plugin.modules().get("moderation");
    return moderation != null && moderation.vanish() != null && moderation.vanish().isVanished(player);
  }

  private boolean bannedHold(Player player) {
    ModerationModule moderation = this.plugin.modules().get("moderation");
    return moderation != null && moderation.holds() != null && moderation.holds().holding(player.getUniqueId());
  }

  private void restoreDeathBack(Player player) {
    if (!this.plugin.configs().players().getBoolean("back.include-death", true)) {
      return;
    }
    UUID uuid = player.getUniqueId();
    Tasks.async(this.plugin, () -> {
      Optional<DeathSnapshot> death = this.repository.lastDeath(uuid);
      Tasks.sync(this.plugin, () -> {
        if (!player.isOnline() || this.plugin.lastLocations().peek(uuid).isPresent()) {
          return;
        }
        death.map(DeathSnapshot::toLocation).ifPresent(location -> this.plugin.lastLocations().put(uuid, location));
      });
    });
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onDamage(EntityDamageEvent event) {
    if (event.getEntity() instanceof Player player && this.god.isGod(player)) {
      event.setCancelled(true);
      player.setFireTicks(0);
    }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onFood(FoodLevelChangeEvent event) {
    if (event.getEntity() instanceof Player player && this.god.isGod(player)) {
      event.setCancelled(true);
    }
  }

  private static String ipOf(Player player) {
    InetSocketAddress address = player.getAddress();
    if (address == null || address.getAddress() == null) {
      return "";
    }
    return address.getAddress().getHostAddress();
  }
}
