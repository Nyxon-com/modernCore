package com.valaryrpg.core.module.moderation;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.players.PlayersModule;
import com.valaryrpg.core.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ban a tempo: il player resta connesso in un dialog nativo col countdown, senza Connection Lost statico.
 */
public final class BanHoldService {

    private final ValaryCorePlugin plugin;
    private final Map<UUID, Punishment> incoming = new ConcurrentHashMap<>();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> allowingTeleport = ConcurrentHashMap.newKeySet();
    private BukkitTask task;

    public BanHoldService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void startTimer() {
        stopTimer();
        int ticks = Math.max(20, this.plugin.configs().moderation().getInt("screen.tick-ticks", 20));
        this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, ticks, ticks);
    }

    public void stopTimer() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        for (UUID uuid : java.util.List.copyOf(this.sessions.keySet())) {
            Session session = this.sessions.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && session != null) {
                player.kick(DisconnectScreens.of(this.plugin, session.punishment));
            }
        }
        this.incoming.clear();
    }

    public boolean liveEnabled() {
        return this.plugin.configs().moderation().getBoolean("screen.live-countdown", true);
    }

    public boolean shouldHold(Punishment punishment) {
        if (this.plugin.configs().moderation().getBoolean("screen.instant", true)) {
            return false;
        }
        return liveEnabled() && punishment != null && punishment.expiresAt() != null && !punishment.expired();
    }

    public void rememberIncoming(UUID uuid, Punishment punishment) {
        if (uuid != null) {
            this.incoming.put(uuid, punishment);
        }
    }

    public boolean holding(UUID uuid) {
        return uuid != null && this.sessions.containsKey(uuid);
    }

    public boolean allowingTeleport(UUID uuid) {
        return this.allowingTeleport.contains(uuid);
    }

    public void onJoin(Player player) {
        Punishment punishment = this.incoming.remove(player.getUniqueId());
        if (!shouldHold(punishment)) {
            return;
        }
        start(player, punishment);
    }

    public void start(Player player, Punishment punishment) {
        if (player == null || !shouldHold(punishment)) {
            return;
        }
        Session session = new Session(punishment, player.getGameMode(), player.getLocation().clone());
        this.sessions.put(player.getUniqueId(), session);
        apply(player, session);
        show(player, session);
        Tasks.later(this.plugin, 3L, () -> {
            if (!player.isOnline() || !holding(player.getUniqueId())) {
                return;
            }
            session.freeze = player.getLocation().clone();
            apply(player, session);
            show(player, session);
        });
    }

    public void leave(Player player) {
        Session session = this.sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        player.kick(DisconnectScreens.of(this.plugin, session.punishment));
    }

    public void release(Player player, boolean expired) {
        Session session = this.sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        restore(player, session);
        if (expired) {
            this.plugin.messages().send(player, "moderation.ban-expired");
        }
    }

    public void forget(UUID uuid) {
        if (uuid == null) {
            return;
        }
        this.sessions.remove(uuid);
        this.incoming.remove(uuid);
    }

    public void unban(UUID uuid) {
        if (uuid == null) {
            return;
        }
        this.incoming.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            release(player, false);
            return;
        }
        this.sessions.remove(uuid);
    }

    private void tick() {
        for (UUID uuid : java.util.List.copyOf(this.sessions.keySet())) {
            Session session = this.sessions.get(uuid);
            Player online = Bukkit.getPlayer(uuid);
            if (online == null || !online.isOnline() || session == null) {
                this.sessions.remove(uuid);
                continue;
            }
            if (session.punishment.expired()) {
                release(online, true);
                continue;
            }
            pin(online, session);
            show(online, session);
        }
    }

    private void apply(Player player, Session session) {
        pin(player, session);
        player.setGameMode(gamemode());
        player.setInvulnerable(true);
        player.setInvisible(true);
        if (this.plugin.configs().moderation().getBoolean("screen.hide-from-tab", true)) {
            player.playerListName(net.kyori.adventure.text.Component.empty());
        }
        hideFromOthers(player);
    }

    private void pin(Player player, Session session) {
        if (session.freeze == null || session.freeze.getWorld() == null) {
            return;
        }
        Location here = player.getLocation();
        if (here.getWorld() != null && here.getWorld().equals(session.freeze.getWorld())
                && here.distanceSquared(session.freeze) < 0.04D) {
            return;
        }
        this.allowingTeleport.add(player.getUniqueId());
        try {
            player.teleport(session.freeze);
        } finally {
            this.allowingTeleport.remove(player.getUniqueId());
        }
    }

    private void show(Player player, Session session) {
        player.showDialog(DisconnectScreens.live(this.plugin, session.punishment));
    }

    private void restore(Player player, Session session) {
        player.closeDialog();
        player.setInvulnerable(false);
        player.setInvisible(false);
        player.setGameMode(session.previous);
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(this.plugin, player);
        }
        PlayersModule players = this.plugin.modules().get("players");
        if (players != null && players.fly() != null) {
            players.fly().reapply(player);
        }
        if (players != null && players.nicks() != null) {
            players.nicks().reapply(player);
        }
    }

    private void hideFromOthers(Player held) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(held)) {
                other.hidePlayer(this.plugin, held);
            }
        }
    }

    private GameMode gamemode() {
        String raw = this.plugin.configs().moderation().getString("screen.gamemode", "SPECTATOR");
        try {
            return GameMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            return GameMode.SPECTATOR;
        }
    }

    private static final class Session {
        private final Punishment punishment;
        private final GameMode previous;
        private Location freeze;

        private Session(Punishment punishment, GameMode previous, Location freeze) {
            this.punishment = punishment;
            this.previous = previous;
            this.freeze = freeze;
        }
    }
}
