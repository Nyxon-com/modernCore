//made with love by nyxon

package com.modern.core.module.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class ChatListener implements Listener {

  private final ChatService chat;

  public ChatListener(ChatService chat) {
    this.chat = chat;
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onChat(AsyncChatEvent event) {
    String plain = event.signedMessage().message();
    if (this.chat.blocked(event.getPlayer(), plain, false)) {
      event.setCancelled(true);
    }
  }
}
