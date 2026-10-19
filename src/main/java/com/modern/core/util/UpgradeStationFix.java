package com.modern.core.util;

import com.modern.core.ModernCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;

/**
 * Nella fucina upgrade l'item da potenziare e' anche in ingredients (per mostrarlo
 * in GUI). MMOItems dopo l'upgrade lo consuma con takeAway: qui lo restituiamo
 * gia' potenziato.
 */
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
                // a volte getRecipe torna la recipe base; prova recipeInfo
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
            // ignore
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
            // Cattura il CheckedUpgradingRecipe: dopo whenUsed ha gia' l'MMOItem upgradato
            Bukkit.getScheduler().runTask(this.plugin, () -> giveUpgradedBack(player, recipeInfo));
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
    }

    private void giveUpgradedBack(Player player, Object recipeInfo) {
        try {
            // CheckedUpgradingRecipe#getMMOItem
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
            // takeAway ha appena consumato l'item usato come ingrediente:
            // ridiamo la versione gia' potenziata tenuta in LiveMMOItem.
            var leftover = player.getInventory().addItem(stack);
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
    }
}
