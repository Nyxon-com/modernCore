package com.modern.core.module.kits;

import com.modern.core.ModernCorePlugin;
import com.modern.core.storage.StorageException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ultimo claim kit per giocatore. Cache RAM, SQL solo in async.
 */
final class KitClaimRepository {

    private final ModernCorePlugin plugin;
    private final Map<UUID, ConcurrentHashMap<String, Long>> cache = new ConcurrentHashMap<>();

    KitClaimRepository(ModernCorePlugin plugin) {
        this.plugin = plugin;
    }

    boolean loaded(UUID uuid) {
        return this.cache.containsKey(uuid);
    }

    Map<String, Long> snapshot(UUID uuid) {
        ConcurrentHashMap<String, Long> cached = this.cache.get(uuid);
        return cached == null ? Map.of() : Map.copyOf(cached);
    }

    void forget(UUID uuid) {
        this.cache.remove(uuid);
    }

    void clear() {
        this.cache.clear();
    }

    Optional<Long> lastClaim(UUID uuid, String kit) {
        ConcurrentHashMap<String, Long> cached = this.cache.get(uuid);
        if (cached == null) {
            lastClaims(uuid);
            cached = this.cache.get(uuid);
        }
        if (cached == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cached.get(kit));
    }

    Map<String, Long> lastClaims(UUID uuid) {
        String sql = "SELECT kit, claimed_at FROM " + table() + " WHERE uuid = ?";
        ConcurrentHashMap<String, Long> last = new ConcurrentHashMap<>();
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    last.put(result.getString("kit"), result.getLong("claimed_at"));
                }
            }
        } catch (SQLException exception) {
            throw new StorageException("Lettura kit claim fallita.", exception);
        }
        this.cache.put(uuid, last);
        return Map.copyOf(last);
    }

    void mark(UUID uuid, String kit, long at) {
        String sql = this.plugin.storage().type().isRemote()
                ? "INSERT INTO " + table() + " (uuid, kit, claimed_at) VALUES (?,?,?) ON DUPLICATE KEY UPDATE claimed_at = VALUES(claimed_at)"
                : "INSERT INTO " + table() + " (uuid, kit, claimed_at) VALUES (?,?,?) ON CONFLICT(uuid, kit) DO UPDATE SET claimed_at = excluded.claimed_at";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, kit);
            statement.setLong(3, at);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Salvataggio kit claim fallito.", exception);
        }
        this.cache.compute(uuid, (ignored, current) -> {
            ConcurrentHashMap<String, Long> next = current == null ? new ConcurrentHashMap<>() : current;
            next.put(kit, at);
            return next;
        });
    }

    private String table() {
        return this.plugin.storage().table("kit_claims");
    }
}
