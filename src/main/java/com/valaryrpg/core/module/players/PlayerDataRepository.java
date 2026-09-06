package com.valaryrpg.core.module.players;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.storage.StorageException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class PlayerDataRepository {

    private final ValaryCorePlugin plugin;

    public PlayerDataRepository(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public Optional<PlayerProfile> find(UUID uuid) {
        String sql = "SELECT name, nick, fly, social_spy, first_join, last_join, last_ip FROM " + table() + " WHERE uuid = ?";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PlayerProfile(
                        uuid,
                        result.getString("name"),
                        result.getString("nick"),
                        result.getInt("fly") == 1,
                        result.getInt("social_spy") == 1,
                        result.getLong("first_join"),
                        result.getLong("last_join"),
                        result.getString("last_ip")
                ));
            }
        } catch (SQLException exception) {
            throw new StorageException("Lettura profilo fallita.", exception);
        }
    }

    public void touchJoin(UUID uuid, String name, String ip, long now) {
        String sql = this.plugin.storage().type().isRemote()
                ? "INSERT INTO " + table() + " (uuid, name, nick, fly, social_spy, first_join, last_join, last_ip) VALUES (?,?,NULL,0,0,?,?,?) "
                + "ON DUPLICATE KEY UPDATE name=VALUES(name), last_join=VALUES(last_join), last_ip=VALUES(last_ip)"
                : "INSERT INTO " + table() + " (uuid, name, nick, fly, social_spy, first_join, last_join, last_ip) VALUES (?,?,NULL,0,0,?,?,?) "
                + "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, last_join=excluded.last_join, last_ip=excluded.last_ip";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.setLong(3, now);
            statement.setLong(4, now);
            statement.setString(5, ip == null ? "" : ip);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Aggiornamento join fallito.", exception);
        }
    }

    public void updateNick(UUID uuid, String nick) {
        updateColumn(uuid, "nick", nick);
    }

    public void updateFly(UUID uuid, boolean fly) {
        updateFlag(uuid, "fly", fly);
    }

    public void updateSocialSpy(UUID uuid, boolean enabled) {
        updateFlag(uuid, "social_spy", enabled);
    }

    private void updateColumn(UUID uuid, String column, String value) {
        String sql = "UPDATE " + table() + " SET " + column + " = ? WHERE uuid = ?";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Aggiornamento " + column + " fallito.", exception);
        }
    }

    private void updateFlag(UUID uuid, String column, boolean value) {
        String sql = "UPDATE " + table() + " SET " + column + " = ? WHERE uuid = ?";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, value ? 1 : 0);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Aggiornamento " + column + " fallito.", exception);
        }
    }

    private String table() {
        return this.plugin.storage().table("players");
    }
}
