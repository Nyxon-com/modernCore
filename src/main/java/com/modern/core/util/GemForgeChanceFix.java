package com.modern.core.util;

import com.modern.core.ModernCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fucina gemme: craft sempre riuscito.
 * Le chance 30/35/50% stanno su success-rate (socket su arma/armor).
 */
public final class GemForgeChanceFix implements Listener {

    private final ModernCorePlugin plugin;

    public GemForgeChanceFix(ModernCorePlugin plugin) {
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
                    EventPriority.HIGH,
                    executor,
                    this.plugin,
                    true
            );
        } catch (ClassNotFoundException ex) {
            this.plugin.getLogger().warning("GemForgeChanceFix: evento MMOItems non trovato.");
        }
    }

    private void onStationUse(Event event) {
        try {
            Object station = event.getClass().getMethod("getStation").invoke(event);
            if (station == null) {
                return;
            }
            Object stationId = station.getClass().getMethod("getId").invoke(station);
            if (stationId == null || !"fucina-gemme".equalsIgnoreCase(String.valueOf(stationId))) {
                return;
            }

            Object interaction = event.getClass().getMethod("getInteraction").invoke(event);
            String action = interaction == null ? "" : String.valueOf(interaction);
            // Solo craft reale, non preview/interact
            if (!action.contains("INSTANT_RECIPE") && !action.contains("CRAFTING_QUEUE")) {
                return;
            }

            Object recipe = event.getClass().getMethod("getRecipe").invoke(event);
            if (recipe == null) {
                return;
            }
            String recipeId = String.valueOf(recipe.getClass().getMethod("getId").invoke(recipe));
            double chance = chanceForRecipe(recipeId);
            if (chance >= 100.0) {
                return;
            }
            if (ThreadLocalRandom.current().nextDouble(100.0) < chance) {
                // successo: lascia procedere MMOItems
                return;
            }

            // FAIL: consuma ingredienti e blocca output
            Object info = event.getClass().getMethod("getRecipeInfo").invoke(event);
            if (info != null) {
                Object ingredients = info.getClass().getMethod("getIngredients").invoke(info);
                if (ingredients instanceof List<?> list) {
                    for (Object checked : list) {
                        try {
                            checked.getClass().getMethod("takeAway").invoke(checked);
                        } catch (ReflectiveOperationException ignored) {
                            // ignore single ingredient failure
                        }
                    }
                }
            }

            event.getClass().getMethod("setCancelled", boolean.class).invoke(event, true);

            Object playerData = event.getClass().getMethod("getPlayerData").invoke(event);
            Object playerObj = playerData.getClass().getMethod("getPlayer").invoke(playerData);
            if (playerObj instanceof Player player) {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 0.7f);
                player.sendMessage("§5Valary RPG§8» §cLa forgia delle gemme e' fallita! §7(chance §f"
                        + (int) chance + "%§7) Materiali persi.");
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
    }

    static double chanceForRecipe(String recipeId) {
        // Le % 30/35/50 sono sul socket (success-rate gemma), non sul craft in fucina.
        return 100.0;
    }
}
