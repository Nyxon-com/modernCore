//made with love by nyxon

package com.modern.core.module.tomb;

import com.modern.core.ModernCorePlugin;
import com.modern.core.util.ItemStacks;
import com.modern.core.util.MmoItems;
import com.modern.core.util.Perms;
import com.modern.core.util.Sounds;
import com.modern.core.util.Tasks;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class TombService {

  private final ModernCorePlugin plugin;
  private final TombRepository repository;
  private final TombMenus menus;

  public TombService(ModernCorePlugin plugin) {
    this.plugin = plugin;
    this.repository = new TombRepository(plugin);
    this.menus = new TombMenus(plugin, this);
  }

  public TombMenus menus() {
    return this.menus;
  }

  public TombRepository repository() {
    return this.repository;
  }

  public boolean enabled() {
    try {
      return yaml().getBoolean("enabled", true);
    } catch (Exception exception) {
      return false;
    }
  }

  public void handleDeath(PlayerDeathEvent event) {
    try {
      if (!enabled()) {
        return;
      }
      Player player = event.getEntity();
      World world = player.getWorld();
      if (keepsInventory(world) || disabledWorld(world)) {
        return;
      }

      ItemStack[] loot = captureLoot(event, player);
      suppressGroundDrops(event, player);

      if (counted(loot) <= 0 && xpMode().equals("lose")) {
        return;
      }

      int storedXp = 0;
      switch (xpMode()) {
        case "keep" -> {
          event.setKeepLevel(true);
          event.setDroppedExp(0);
        }
        case "store" -> {
          storedXp = Math.max(0, player.getTotalExperience());
          event.setDroppedExp(0);
          event.setNewExp(0);
          event.setNewLevel(0);
          event.setNewTotalExp(0);
        }
        default -> event.setDroppedExp(0);
      }

      String inventory = ItemStacks.encode(loot);
      Location location = player.getLocation();
      String cause = player.getLastDamageCause() == null
          ? "UNKNOWN"
          : player.getLastDamageCause().getCause().name();
      String killer = event.getEntity().getKiller() == null ? "" : event.getEntity().getKiller().getName();
      Tomb draft = new Tomb(
          0L,
          player.getUniqueId(),
          player.getName(),
          System.currentTimeMillis(),
          location.getWorld() == null ? "" : location.getWorld().getName(),
          location.getX(),
          location.getY(),
          location.getZ(),
          cause,
          killer,
          inventory,
          storedXp
      );
      int max = Math.max(1, yaml().getInt("max-tombs", 10));
      UUID uuid = player.getUniqueId();
      Tasks.async(this.plugin, () -> {
        try {
          long id = this.repository.insert(draft);
          this.repository.trim(uuid, max);
          if (yaml().getBoolean("notify-on-death", true)) {
            Tasks.sync(this.plugin, () -> {
              if (!player.isOnline()) {
                return;
              }
              this.plugin.messages().send(player, "tomb.created", Map.of(
                  "id", String.valueOf(id),
                  "price", formatPrice(),
                  "label", commandLabel()
              ));
              Sounds.playRaw(this.plugin, player, yaml().getString("sounds.death", ""), "gui.click-sound");
            });
          }
        } catch (Exception exception) {
          this.plugin.getLogger().warning("Tomba SQL fallita per " + player.getName() + ": " + exception.getMessage());
        }
      });
    } catch (Exception exception) {
      this.plugin.getLogger().warning("Tomba morte fallita: " + exception.getMessage());
      exception.printStackTrace();
    }
  }

  public void ensureNoGroundDrops(PlayerDeathEvent event) {
    if (!enabled()) {
      return;
    }
    World world = event.getEntity().getWorld();
    if (keepsInventory(world) || disabledWorld(world)) {
      return;
    }
    event.getDrops().clear();
    event.setDroppedExp(0);
  }

  private static ItemStack[] captureLoot(PlayerDeathEvent event, Player player) {
    List<ItemStack> loot = new ArrayList<>();
    boolean anyDrop = false;
    for (ItemStack item : event.getDrops()) {
      if (item == null || item.getType().isAir()) {
        continue;
      }
      anyDrop = true;
      if (MmoItems.shouldKeepOnDeath(item, player)) {

        continue;
      }
      loot.add(item.clone());
    }
    if (anyDrop) {
      return loot.toArray(ItemStack[]::new);
    }

    ItemStack[] packed = ItemStacks.decode(ItemStacks.encodePlayer(player.getInventory()));
    List<ItemStack> fallbackLoot = new ArrayList<>();
    for (ItemStack item : packed) {
      if (item == null || item.getType().isAir()) {
        continue;
      }
      if (MmoItems.shouldKeepOnDeath(item, player)) {
        continue;
      }
      fallbackLoot.add(item.clone());
    }
    return fallbackLoot.toArray(ItemStack[]::new);
  }

  private static void suppressGroundDrops(PlayerDeathEvent event, Player player) {

    event.getDrops().removeIf(item -> item == null || item.getType().isAir()
        || MmoItems.shouldKeepOnDeath(item, player));
    event.getDrops().clear();
    event.setDroppedExp(0);

    event.setKeepInventory(false);
    PlayerInventory inventory = player.getInventory();
    inventory.clear();
    inventory.setHelmet(null);
    inventory.setChestplate(null);
    inventory.setLeggings(null);
    inventory.setBoots(null);
    inventory.setItemInOffHand(null);
  }

  private static boolean keepsInventory(World world) {
    if (world == null) {
      return false;
    }

    try {
      return Boolean.TRUE.equals(world.getGameRuleValue(GameRules.KEEP_INVENTORY));
    } catch (Throwable ignored) {
      return false;
    }
  }

  public void openList(Player player, int page) {
    this.menus.openList(player, page);
  }

  public void openView(Player player, long tombId, int fromPage) {
    Tasks.async(this.plugin, () -> {
      Tomb tomb = this.repository.find(tombId).orElse(null);
      Tasks.sync(this.plugin, () -> {
        if (tomb == null) {
          this.plugin.messages().send(player, "tomb.missing");
          return;
        }
        if (!tomb.uuid().equals(player.getUniqueId()) && !Perms.has(this.plugin, player, "tomb-others")) {
          this.plugin.messages().send(player, "general.no-permission");
          return;
        }
        this.menus.openView(player, tomb, fromPage);
      });
    });
  }

  public void claim(Player player, long tombId) {
    Tasks.async(this.plugin, () -> {
      Tomb tomb = this.repository.find(tombId).orElse(null);
      Tasks.sync(this.plugin, () -> {
        if (!player.isOnline()) {
          return;
        }
        if (tomb == null) {
          this.plugin.messages().send(player, "tomb.missing");
          return;
        }
        if (!tomb.uuid().equals(player.getUniqueId()) && !Perms.has(this.plugin, player, "tomb-others")) {
          this.plugin.messages().send(player, "general.no-permission");
          return;
        }
        if (!charge(player)) {
          return;
        }
        giveLoot(player, tomb);
        boolean delete = yaml().getBoolean("claim.delete-after-claim", true);
        if (delete) {
          Tasks.async(this.plugin, () -> this.repository.delete(tomb.id()));
        }
        this.plugin.messages().send(player, "tomb.claimed", Map.of(
            "id", String.valueOf(tomb.id()),
            "price", formatPrice()
        ));
        Sounds.playRaw(this.plugin, player, yaml().getString("sounds.claim", ""), "gui.click-sound");
        player.closeInventory();
      });
    });
  }

  public void maybeAutoOpen(Player player) {
    if (!enabled() || !yaml().getBoolean("auto-open-on-respawn", true)) {
      return;
    }
    long delay = Math.max(1L, yaml().getLong("auto-open-delay-ticks", 40L));
    Tasks.later(this.plugin, delay, () -> {
      if (player.isOnline()) {
        openList(player, 1);
      }
    });
  }

  public double priceAmount() {
    if (!yaml().getBoolean("price.enabled", true)) {
      return 0.0D;
    }
    return Math.max(0.0D, yaml().getDouble("price.amount", 0.0D));
  }

  public String formatPrice() {
    double amount = priceAmount();
    if (amount <= 0.0D) {
      return this.plugin.messages().text("tomb.price-free");
    }
    return String.format(Locale.ROOT, "%.0f", amount);
  }

  public String formatBalance(Player player) {
    if (this.plugin.economy() == null || !this.plugin.economy().available()) {
      return "0";
    }
    return String.format(Locale.ROOT, "%.0f", this.plugin.economy().balance(player));
  }

  public Map<String, String> economyPlaceholders(Player player) {
    Map<String, String> map = new HashMap<>();
    map.put("price", formatPrice());
    map.put("balance", formatBalance(player));
    map.put("player", player.getName());
    map.put("label", commandLabel());
    return map;
  }

  private boolean charge(Player player) {
    if (Perms.has(this.plugin, player, "tomb-free")) {
      return true;
    }
    double amount = priceAmount();
    if (amount <= 0.0D) {
      return true;
    }
    if (this.plugin.economy() == null || !this.plugin.economy().available()) {
      this.plugin.messages().send(player, "tomb.economy-missing");
      Sounds.playRaw(this.plugin, player, yaml().getString("sounds.fail", ""), "gui.click-sound");
      return false;
    }
    if (!this.plugin.economy().has(player, amount)) {
      this.plugin.messages().send(player, "tomb.not-enough", Map.of(
          "price", formatPrice(),
          "balance", formatBalance(player)
      ));
      Sounds.playRaw(this.plugin, player, yaml().getString("sounds.fail", ""), "gui.click-sound");
      return false;
    }
    if (!this.plugin.economy().withdraw(player, amount)) {
      this.plugin.messages().send(player, "tomb.not-enough", Map.of(
          "price", formatPrice(),
          "balance", formatBalance(player)
      ));
      return false;
    }
    return true;
  }

  private void giveLoot(Player player, Tomb tomb) {
    ItemStack[] packed = ItemStacks.decode(tomb.inventory());
    boolean dropOverflow = yaml().getBoolean("claim.drop-overflow", true);
    for (ItemStack item : packed) {
      if (item == null || item.getType().isAir()) {
        continue;
      }
      HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
      if (dropOverflow) {
        for (ItemStack drop : leftover.values()) {
          player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
      }
    }
    if (tomb.xp() > 0 && xpMode().equals("store")) {
      player.giveExp(tomb.xp());
    }
  }

  private boolean disabledWorld(World world) {
    if (world == null) {
      return true;
    }
    List<String> disabled = yaml().getStringList("disabled-worlds");
    for (String entry : disabled) {
      if (entry != null && entry.equalsIgnoreCase(world.getName())) {
        return true;
      }
    }
    return false;
  }

  private String xpMode() {
    return yaml().getString("xp-mode", "store").toLowerCase(Locale.ROOT);
  }

  private String commandLabel() {
    String label = this.plugin.configs().root().getString("commands.tomb", "tomba");
    return label == null || label.isBlank() ? "tomba" : label;
  }

  FileConfiguration yaml() {
    return this.plugin.configs().tomb();
  }

  private static int counted(ItemStack[] items) {
    int count = 0;
    if (items == null) {
      return 0;
    }
    for (ItemStack item : items) {
      if (item != null && !item.getType().isAir()) {
        count += item.getAmount();
      }
    }
    return count;
  }
}
