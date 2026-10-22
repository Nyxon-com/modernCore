//made with love by nyxon

package com.modern.core.module.players;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

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
        String name = nickOrNull;
        if (name != null && !name.isBlank() && !name.contains("<gray>") && !name.contains("&7")) {
          name = "<gray>" + name + "</gray>";
        }
        method.invoke(list, tabPlayer, name);
        return;
      }
    } catch (ReflectiveOperationException ignored) {

    }
  }
}
