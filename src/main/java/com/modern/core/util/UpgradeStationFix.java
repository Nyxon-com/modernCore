package com.modern.core.util;

import com.modern.core.ModernCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;

public final class UpgradeStationFix implements Listener {

  private final ModernCorePlugin plugin;

  public UpgradeStationFix(ModernCorePlugin plugin) {
    this.plugin = plugin;
    register();
  }

  private void register() {
    if (Bukkit.getPluginManager().getPlugin("MMOItems") == null) {
      return;
    }
    try {
      Class<? extends Event> eventClass = Class
          .forName("net.Indyuce.mmoitems.api.event.PlayerUseCraftingStationEvent")
          .asSubclass(Event.class);
      EventExecutor executor = (listener, event) -> onStationUse(event);
      Bukkit.getPluginManager().registerEvent(
          eventClass,
          this,
          EventPriority.MONITOR,
          executor,
          this.plugin,
          true
      );
    } catch (ClassNotFoundException ignored) {
      this.plugin.getLogger().warning("UpgradeStationFix: evento MMOItems non trovato.");
    }
  }

  private void onStationUse(Event event) {
    try {
      if ((boolean) event.getClass().getMethod("isCancelled").invoke(event)) {
        return;
      }
      Object station = event.getClass().getMethod("getStation").invoke(event);
      if (station != null) {
        Object stationId = station.getClass().getMethod("getId").invoke(station);
        if (stationId != null) {
          String id = String.valueOf(stationId).toLowerCase(java.util.Locale.ROOT);
          if (!"fucina-upgrade".equals(id) && !"fucina-upgrade-marte".equals(id)) {
            return;
          }
        }
      }
      Object recipe = event.getClass().getMethod("getRecipe").invoke(event);
      if (recipe == null) {
        return;
      }
      Class<?> upgradingClass = Class.forName("net.Indyuce.mmoitems.api.crafting.recipe.UpgradingRecipe");
      if (!upgradingClass.isInstance(recipe)) {

        Object info = event.getClass().getMethod("getRecipeInfo").invoke(event);
        if (info == null) {
          return;
        }
        Object nested = info.getClass().getMethod("getRecipe").invoke(info);
        if (nested == null || !upgradingClass.isInstance(nested)) {
          return;
        }
        restoreNextTick(event, info);
        return;
      }
      Object info = event.getClass().getMethod("getRecipeInfo").invoke(event);
      restoreNextTick(event, info);
    } catch (ReflectiveOperationException ignored) {

    }
  }

  private void restoreNextTick(Event event, Object recipeInfo) {
    if (recipeInfo == null) {
      return;
    }
    try {
      Object playerData = event.getClass().getMethod("getPlayerData").invoke(event);
      Object playerObj = playerData.getClass().getMethod("getPlayer").invoke(playerData);
      if (!(playerObj instanceof Player player)) {
        return;
      }

      Bukkit.getScheduler().runTask(this.plugin, () -> giveUpgradedBack(player, recipeInfo));
    } catch (ReflectiveOperationException ignored) {

    }
  }

  private void giveUpgradedBack(Player player, Object recipeInfo) {
    try {

      Object mmoItem;
      try {
        mmoItem = recipeInfo.getClass().getMethod("getMMOItem").invoke(recipeInfo);
      } catch (NoSuchMethodException ex) {
        return;
      }
      if (mmoItem == null) {
        return;
      }
      Object builder = mmoItem.getClass().getMethod("newBuilder").invoke(mmoItem);
      Object built = builder.getClass().getMethod("build").invoke(builder);
      if (!(built instanceof ItemStack stack) || stack.getType().isAir()) {
        return;
      }

      var leftover = player.getInventory().addItem(stack);
      leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    } catch (ReflectiveOperationException ignored) {

    }
  }
}
