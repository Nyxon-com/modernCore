package com.valaryrpg.core.module.players;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * TAB (NEZNAMY) sovrascrive playerListName: il nick va impostato via API se il plugin c'è.
 */
public final class TabNickHook {

    private TabNickHook() {
    }

    public static void apply(Player player, String nickOrNull) {
        if (Bukkit.getPluginManager().getPlugin("TAB") == null) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            if (api == null) {
                return;
            }
            Object tabPlayer = apiClass.getMethod("getPlayer", UUID.class).invoke(api, player.getUniqueId());
            if (tabPlayer == null) {
                return;
            }
            Object list = apiClass.getMethod("getTabListFormatManager").invoke(api);
            if (list == null) {
                return;
            }
            for (Method method : list.getClass().getMethods()) {
                if (!"setName".equals(method.getName()) || method.getParameterCount() != 2) {
                    continue;
                }
                method.invoke(list, tabPlayer, nickOrNull);
                return;
            }
        } catch (ReflectiveOperationException ignored) {
            // TAB assente o API diversa: resta playerListName vanilla.
        }
    }
}
