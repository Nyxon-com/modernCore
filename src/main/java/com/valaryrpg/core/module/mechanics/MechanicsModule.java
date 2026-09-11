package com.valaryrpg.core.module.mechanics;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.Module;
import com.valaryrpg.core.module.players.PlayerDataRepository;
import org.bukkit.event.HandlerList;

/**
 * LimitY, morti, hologram a terra, long-jump, portali e holo-block.
 */
public final class MechanicsModule implements Module {

    private final ValaryCorePlugin plugin;
    private LimitYService limitY;
    private DeathService deaths;
    private GroundItemService groundItems;
    private LongJumpService longJumps;
    private PortalService portals;
    private HoloBlockService holos;
    private MechanicsListener listener;

    public MechanicsModule(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "mechanics";
    }

    @Override
    public void enable() {
        PlayerDataRepository players = new PlayerDataRepository(this.plugin);
        this.limitY = new LimitYService(this.plugin);
        this.deaths = new DeathService(this.plugin, players);
        this.groundItems = new GroundItemService(this.plugin);
        this.longJumps = new LongJumpService(this.plugin);
        this.portals = new PortalService(this.plugin);
        this.holos = new HoloBlockService(this.plugin);
        this.listener = new MechanicsListener(this.plugin, this.deaths, this.groundItems, this.longJumps, this.portals, this.holos);
        this.plugin.getServer().getPluginManager().registerEvents(this.listener, this.plugin);
        this.limitY.start();
        this.groundItems.start();
        this.longJumps.start();
        this.holos.start();
    }

    @Override
    public void disable() {
        if (this.limitY != null) {
            this.limitY.stop();
        }
        if (this.longJumps != null) {
            this.longJumps.stop();
        }
        if (this.groundItems != null) {
            this.groundItems.clear();
        }
        if (this.holos != null) {
            this.holos.stop();
        }
        if (this.listener != null) {
            HandlerList.unregisterAll(this.listener);
        }
    }

    @Override
    public void reload() {
        disable();
        enable();
    }

    public LimitYService limitY() {
        return this.limitY;
    }

    public DeathService deaths() {
        return this.deaths;
    }

    public PortalService portals() {
        return this.portals;
    }

    public LongJumpService longJumps() {
        return this.longJumps;
    }

    public HoloBlockService holos() {
        return this.holos;
    }
}
