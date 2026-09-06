package com.valaryrpg.core.module.players;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.Module;
import org.bukkit.event.HandlerList;

public final class PlayersModule implements Module {

    private final ValaryCorePlugin plugin;
    private PlayerDataRepository repository;
    private NickService nicks;
    private FlyService fly;
    private PrivateMessageService messages;
    private PlayersListener listener;

    public PlayersModule(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "players";
    }

    @Override
    public void enable() {
        this.repository = new PlayerDataRepository(this.plugin);
        this.nicks = new NickService(this.plugin, this.repository);
        this.fly = new FlyService(this.plugin, this.repository);
        this.messages = new PrivateMessageService(this.plugin, this.repository, this.nicks);
        this.listener = new PlayersListener(this.plugin, this.repository, this.nicks, this.fly, this.messages);
        this.plugin.getServer().getPluginManager().registerEvents(this.listener, this.plugin);
    }

    @Override
    public void disable() {
        if (this.listener != null) {
            HandlerList.unregisterAll(this.listener);
        }
    }

    @Override
    public void reload() {
        disable();
        enable();
    }

    public NickService nicks() {
        return this.nicks;
    }

    public FlyService fly() {
        return this.fly;
    }

    public PrivateMessageService messages() {
        return this.messages;
    }
}
