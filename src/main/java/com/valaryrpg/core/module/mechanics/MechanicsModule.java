package com.valaryrpg.core.module.mechanics;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.Module;
import com.valaryrpg.core.module.players.PlayerDataRepository;
import com.valaryrpg.core.module.tomb.TombListener;
import com.valaryrpg.core.module.tomb.TombService;
import org.bukkit.event.HandlerList;

/**
 * LimitY, morti, tombe, hologram a terra, long-jump, portali e holo-block.
 */
public final class MechanicsModule implements Module {

    private final ValaryCorePlugin plugin;
    private LimitYService limitY;
    private DeathService deaths;
    private TombService tombs;
    private GroundItemService groundItems;
    private ClearLagService clearLag;
    private LongJumpService longJumps;
    private PortalService portals;
    private HoloBlockService holos;
    private AdventurePickaxeService pickaxes;
    private MechanicsListener listener;
    private TombListener tombListener;
    private boolean initialized;

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
        this.tombs = new TombService(this.plugin);
        this.groundItems = new GroundItemService(this.plugin);
        this.clearLag = new ClearLagService(this.plugin, this.groundItems);
        this.longJumps = new LongJumpService(this.plugin);
        this.portals = new PortalService(this.plugin);
        this.holos = new HoloBlockService(this.plugin);
        this.pickaxes = new AdventurePickaxeService(this.plugin);
        this.listener = new MechanicsListener(this.plugin, this.deaths, this.groundItems, this.longJumps, this.portals, this.holos);
        this.tombListener = new TombListener(this.tombs);
        this.plugin.getServer().getPluginManager().registerEvents(this.listener, this.plugin);
        this.plugin.getServer().getPluginManager().registerEvents(this.tombListener, this.plugin);
        this.plugin.getServer().getPluginManager().registerEvents(this.pickaxes, this.plugin);
        if (this.initialized) {
            this.limitY.start();
        } else {
            this.limitY.initialize();
            this.initialized = true;
        }
        this.groundItems.start();
        this.clearLag.start();
        this.longJumps.start();
        this.holos.start();
        for (org.bukkit.entity.Player player : this.plugin.getServer().getOnlinePlayers()) {
            this.pickaxes.stampInventory(player);
        }
    }

    @Override
    public void disable() {
        if (this.limitY != null) {
            this.limitY.stop();
        }
        if (this.longJumps != null) {
            this.longJumps.stop();
        }
        if (this.clearLag != null) {
            this.clearLag.stop();
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
        if (this.tombListener != null) {
            HandlerList.unregisterAll(this.tombListener);
        }
        if (this.pickaxes != null) {
            HandlerList.unregisterAll(this.pickaxes);
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

    public TombService tombs() {
        return this.tombs;
    }

    public ClearLagService clearLag() {
        return this.clearLag;
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
