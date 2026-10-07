package com.valaryrpg.core.module.players;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.Module;
import com.valaryrpg.core.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.util.Optional;
import java.util.UUID;

public final class PlayersModule implements Module {

    private final ValaryCorePlugin plugin;
    private PlayerDataRepository repository;
    private NickService nicks;
    private FlyService fly;
    private GodService god;
    private GameModeService gamemodes;
    private VitalService vitals;
    private BackService back;
    private PrivateMessageService messages;
    private PlaytimeService playtime;
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
        this.fly.start();
        this.plugin.getServer().getPluginManager().registerEvents(this.fly, this.plugin);
        this.god = new GodService(this.plugin, this.repository);
        this.gamemodes = new GameModeService(this.plugin, this.repository);
        this.vitals = new VitalService(this.plugin);
        this.back = new BackService(this.plugin);
        this.messages = new PrivateMessageService(this.plugin, this.repository, this.nicks);
        this.playtime = new PlaytimeService(this.plugin, this.repository);
        this.listener = new PlayersListener(
                this.plugin,
                this.repository,
                this.nicks,
                this.fly,
                this.god,
                this.gamemodes,
                this.messages,
                this.playtime
        );
        this.plugin.getServer().getPluginManager().registerEvents(this.listener, this.plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
            this.playtime.startSession(player.getUniqueId());
        }
    }

    private void refresh(Player player) {
        UUID uuid = player.getUniqueId();
        this.gamemodes.restore(player, player.getGameMode().name());
        Tasks.async(this.plugin, () -> {
            Optional<PlayerProfile> profile = Optional.empty();
            boolean godMode = false;
            try {
                profile = this.repository.find(uuid);
                godMode = this.repository.god(uuid);
            } catch (Exception exception) {
                this.plugin.getLogger().warning("Reload player SQL fallito per " + player.getName() + ": " + exception.getMessage());
            }
            Optional<PlayerProfile> loaded = profile;
            boolean restoreGod = godMode;
            Tasks.sync(this.plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                this.nicks.applyStored(player, loaded.map(PlayerProfile::nick).orElse(null));
                this.fly.restore(player, loaded.map(PlayerProfile::fly).orElse(false));
                this.god.restore(player, restoreGod);
                this.messages.loadSpy(player, loaded.map(PlayerProfile::socialSpy).orElse(false));
            });
        });
    }

    @Override
    public void disable() {
        if (this.fly != null) {
            this.fly.stop();
            HandlerList.unregisterAll(this.fly);
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

    public GameModeService gamemodes() {
        return this.gamemodes;
    }

    public PlaytimeService playtime() {
        return this.playtime;
    }

    public PlayerDataRepository repository() {
        return this.repository;
    }
}
