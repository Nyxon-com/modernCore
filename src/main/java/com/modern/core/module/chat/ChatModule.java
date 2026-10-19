package com.modern.core.module.chat;

import com.modern.core.ModernCorePlugin;
import com.modern.core.module.Module;
import org.bukkit.event.HandlerList;

/**
 * Clear chat, annunci e filtro.
 */
public final class ChatModule implements Module {

    private final ModernCorePlugin plugin;
    private ChatService chat;
    private ChatListener listener;

    public ChatModule(ModernCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "chat";
    }

    @Override
    public void enable() {
        this.chat = new ChatService(this.plugin);
        this.listener = new ChatListener(this.chat);
        this.plugin.getServer().getPluginManager().registerEvents(this.listener, this.plugin);
    }

    @Override
    public void disable() {
        if (this.listener != null) {
            HandlerList.unregisterAll(this.listener);
            this.listener = null;
        }
        if (this.chat != null) {
            this.chat.shutdown();
        }
    }

    @Override
    public void reload() {
        disable();
        enable();
    }

    public ChatService chat() {
        return this.chat;
    }
}
