package com.valaryrpg.core.module.players;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.storage.StorageException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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

    public void updateSocialSpy(UUID uuid, boolean spy) {
        updateFlag(uuid, "social_spy", spy);
    }

    public void updateGod(UUID uuid, boolean god) {
        updateFlag(uuid, "god", god);
    }

    public boolean god(UUID uuid) {
        String sql = "SELECT god FROM " + table() + " WHERE uuid = ?";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt("god") == 1;
            }
        } catch (SQLException exception) {
            return false;
        }
    }

    public List<String> namesByIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return List.of();
        }
        String sql = "SELECT name FROM " + table() + " WHERE last_ip = ? ORDER BY last_join DESC";
        List<String> names = new ArrayList<>();
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ip);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    names.add(result.getString("name"));
                }
            }
        } catch (SQLException exception) {
            throw new StorageException("Lettura dupeip fallita.", exception);
        }
        return names;
    }

    public String lastIp(UUID uuid) {
        return find(uuid).map(PlayerProfile::lastIp).orElse("");
    }

    public void updateVanish(UUID uuid, boolean vanished) {
        updateFlag(uuid, "vanished", vanished);
    }

    public boolean vanished(UUID uuid) {
        String sql = "SELECT vanished FROM " + table() + " WHERE uuid = ?";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt("vanished") == 1;
            }
        } catch (SQLException exception) {
            return false;
        }
    }

    public void saveDeath(
            UUID uuid,
            String world,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            long at,
            String cause,
            String killer,
            String inventory
    ) {
        String sql = "UPDATE " + table() + " SET death_world=?, death_x=?, death_y=?, death_z=?, death_yaw=?, death_pitch=?, death_at=?, death_cause=?, death_killer=?, death_inventory=? WHERE uuid=?";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, world);
            statement.setDouble(2, x);
            statement.setDouble(3, y);
            statement.setDouble(4, z);
            statement.setFloat(5, yaw);
            statement.setFloat(6, pitch);
            statement.setLong(7, at);
            statement.setString(8, cause == null ? "" : cause);
            statement.setString(9, killer == null ? "" : killer);
            statement.setString(10, inventory == null ? "" : inventory);
            statement.setString(11, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Salvataggio morte fallito.", exception);
        }
    }

    public Optional<DeathSnapshot> lastDeath(UUID uuid) {
        String sql = "SELECT death_world, death_x, death_y, death_z, death_yaw, death_pitch, death_at, death_cause, death_killer, death_inventory FROM "
                + table() + " WHERE uuid = ? AND death_world IS NOT NULL";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new DeathSnapshot(
                        uuid,
                        result.getString("death_world"),
                        result.getDouble("death_x"),
                        result.getDouble("death_y"),
                        result.getDouble("death_z"),
                        result.getFloat("death_yaw"),
                        result.getFloat("death_pitch"),
                        result.getLong("death_at"),
                        empty(result.getString("death_cause")),
                        empty(result.getString("death_killer")),
                        empty(result.getString("death_inventory"))
                ));
            }
        } catch (SQLException exception) {
            throw new StorageException("Lettura morte fallita.", exception);
        }
    }

    private static String empty(String value) {
        return value == null ? "" : value;
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
