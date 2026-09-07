package com.valaryrpg.core.module.players;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.Module;
import org.bukkit.event.HandlerList;

public final class PlayersModule implements Module {

    private final ValaryCorePlugin plugin;
    private PlayerDataRepository repository;
    private NickService nicks;
    private FlyService fly;
    private GodService god;
    private VitalService vitals;
    private BackService back;
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
        this.god = new GodService(this.plugin, this.repository);
        this.vitals = new VitalService(this.plugin);
        this.back = new BackService(this.plugin);
        this.messages = new PrivateMessageService(this.plugin, this.repository, this.nicks);
        this.listener = new PlayersListener(this.plugin, this.repository, this.nicks, this.fly, this.god, this.messages);
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

    public GodService god() {
        return this.god;
    }

    public VitalService vitals() {
        return this.vitals;
    }

    public BackService back() {
        return this.back;
    }

    public PrivateMessageService messages() {
        return this.messages;
    }
}
