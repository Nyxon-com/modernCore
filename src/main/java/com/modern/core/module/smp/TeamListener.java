package com.modern.core.module.smp;

import io.papermc.paper.event.player.AsyncChatEvent;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class TeamListener implements Listener {
  private final SmpContext plugin;

  public TeamListener(SmpContext plugin) {
    this.plugin = plugin;
  }

  @EventHandler(priority = EventPriority.LOW)
  public void onChat(AsyncChatEvent event) {
    if (!(event.getPlayer() instanceof Player player) || !plugin.teams().teamChat(player)) {
      return;
    }
    String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
        .serialize(event.message());
    event.setCancelled(true);
    Bukkit.getScheduler().runTask(plugin.core(), () -> plugin.teams().sendTeamChat(player, text));
  }
}
