package com.valaryrpg.core.module.kits;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.storage.StorageException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Ultimo claim kit per giocatore (cooldown).
 */
final class KitClaimRepository {

    private final ValaryCorePlugin plugin;

    KitClaimRepository(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    Optional<Long> lastClaim(UUID uuid, String kit) {
        String sql = "SELECT claimed_at FROM " + table() + " WHERE uuid = ? AND kit = ?";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, kit);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(result.getLong("claimed_at"));
            }
        } catch (SQLException exception) {
            throw new StorageException("Lettura kit claim fallita.", exception);
        }
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
    }

    private String table() {
        return this.plugin.storage().table("kit_claims");
    }
}
