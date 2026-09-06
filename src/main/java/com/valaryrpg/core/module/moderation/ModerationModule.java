package com.valaryrpg.core.module.moderation;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.Module;
import com.valaryrpg.core.module.players.PlayerDataRepository;
import org.bukkit.event.HandlerList;

public final class ModerationModule implements Module {

    private final ValaryCorePlugin plugin;
    private ModerationService moderation;
    private VanishService vanish;
    private InvseeService invsee;
    private ModerationListener listener;

    public ModerationModule(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "moderation";
    }

    @Override
    public void enable() {
        PlayerDataRepository players = new PlayerDataRepository(this.plugin);
        this.moderation = new ModerationService(this.plugin, new PunishmentRepository(this.plugin), players);
        this.vanish = new VanishService(this.plugin, players);
        this.invsee = new InvseeService(this.plugin);
        this.listener = new ModerationListener(this.plugin, this.moderation, this.vanish, this.invsee);
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

    public ModerationService moderation() {
        return this.moderation;
    }

    public VanishService vanish() {
        return this.vanish;
    }

    public InvseeService invsee() {
        return this.invsee;
    }
}
