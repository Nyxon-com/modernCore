package com.modern.core.module.holograms;

import com.modern.core.ModernCorePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * Chunk e quit per hologram in cartella holo/.
 */
public final class HologramListener implements Listener {

    private final HologramService holograms;

    public HologramListener(HologramService holograms) {
        this.holograms = holograms;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        this.holograms.onChunkLoad(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        this.holograms.onChunkUnload(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.holograms.unload(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        this.holograms.unload(event.getPlayer().getUniqueId());
    }
}
