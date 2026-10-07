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
        String sql = "SELECT name, nick, fly, social_spy, gamemode, first_join, last_join, last_quit, playtime_ms, last_ip FROM "
                + table() + " WHERE uuid = ?";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapProfile(uuid, result));
            }
        } catch (SQLException exception) {
            throw new StorageException("Lettura profilo fallita.", exception);
        }
    }

    public Optional<PlayerProfile> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT uuid, name, nick, fly, social_spy, gamemode, first_join, last_join, last_quit, playtime_ms, last_ip FROM "
                + table() + " WHERE LOWER(name) = LOWER(?) LIMIT 1";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name.trim());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapProfile(UUID.fromString(result.getString("uuid")), result));
            }
        } catch (SQLException exception) {
            throw new StorageException("Lettura profilo per nome fallita.", exception);
        }
    }

    public List<PlayerProfile> listAll(int limit, int offset) {
        String sql = "SELECT uuid, name, nick, fly, social_spy, gamemode, first_join, last_join, last_quit, playtime_ms, last_ip FROM "
                + table() + " ORDER BY LOWER(name) ASC LIMIT ? OFFSET ?";
        List<PlayerProfile> profiles = new ArrayList<>();
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            statement.setInt(2, Math.max(0, offset));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    profiles.add(mapProfile(UUID.fromString(result.getString("uuid")), result));
                }
            }
        } catch (SQLException exception) {
            throw new StorageException("Lettura playerlist fallita.", exception);
        }
        return profiles;
    }

    public int countPlayers() {
        String sql = "SELECT COUNT(*) FROM " + table();
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getInt(1) : 0;
        } catch (SQLException exception) {
            throw new StorageException("Conteggio player fallito.", exception);
        }
    }

    public List<String> suggestNames(String prefix, int limit) {
        String sql = "SELECT name FROM " + table() + " WHERE LOWER(name) LIKE LOWER(?) ORDER BY LOWER(name) ASC LIMIT ?";
        List<String> names = new ArrayList<>();
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, (prefix == null ? "" : prefix) + "%");
            statement.setInt(2, Math.max(1, limit));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    names.add(result.getString("name"));
                }
            }
        } catch (SQLException exception) {
            return List.of();
        }
        return names;
    }

    public void addPlaytime(UUID uuid, long millis, long quitAt) {
        if (millis <= 0L && quitAt <= 0L) {
            return;
        }
        String sql = "UPDATE " + table() + " SET playtime_ms = playtime_ms + ?, last_quit = ? WHERE uuid = ?";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, Math.max(0L, millis));
            statement.setLong(2, quitAt);
            statement.setString(3, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Aggiornamento playtime fallito.", exception);
        }
    }

    public void saveInventory(UUID uuid, String encoded, boolean dirty) {
        String sql = "UPDATE " + table() + " SET inventory = ?, inventory_dirty = ? WHERE uuid = ?";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, encoded == null ? "" : encoded);
            statement.setInt(2, dirty ? 1 : 0);
            statement.setString(3, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Salvataggio inventario fallito.", exception);
        }
    }

    public Optional<String> inventory(UUID uuid) {
        String sql = "SELECT inventory FROM " + table() + " WHERE uuid = ?";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                String raw = result.getString("inventory");
                return raw == null || raw.isBlank() ? Optional.empty() : Optional.of(raw);
            }
        } catch (SQLException exception) {
            throw new StorageException("Lettura inventario fallita.", exception);
        }
    }

    public boolean inventoryDirty(UUID uuid) {
        String sql = "SELECT inventory_dirty FROM " + table() + " WHERE uuid = ?";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt("inventory_dirty") == 1;
            }
        } catch (SQLException exception) {
            return false;
        }
    }

    public void clearInventoryDirty(UUID uuid) {
        updateFlag(uuid, "inventory_dirty", false);
    }

    private static PlayerProfile mapProfile(UUID uuid, ResultSet result) throws SQLException {
        return new PlayerProfile(
                uuid,
                result.getString("name"),
                result.getString("nick"),
                result.getInt("fly") == 1,
                result.getInt("social_spy") == 1,
                result.getString("gamemode"),
                result.getLong("first_join"),
                result.getLong("last_join"),
                result.getLong("last_quit"),
                result.getLong("playtime_ms"),
                result.getString("last_ip")
        );
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

    public void updateNick(UUID uuid, String name, String nick) {
        upsertString(uuid, name, "nick", nick);
    }

    public void updateFly(UUID uuid, String name, boolean fly) {
        upsertFlag(uuid, name, "fly", fly);
    }

    public void updateSocialSpy(UUID uuid, String name, boolean spy) {
        upsertFlag(uuid, name, "social_spy", spy);
    }

    public void updateGod(UUID uuid, String name, boolean god) {
        upsertFlag(uuid, name, "god", god);
    }

    public void updateGamemode(UUID uuid, String name, String gamemode) {
        upsertString(uuid, name, "gamemode", gamemode);
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

    private void upsertString(UUID uuid, String name, String column, String value) {
        long now = System.currentTimeMillis();
        String sql = this.plugin.storage().type().isRemote()
                ? "INSERT INTO " + table() + " (uuid, name, first_join, last_join, last_ip, " + column + ") VALUES (?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE name=VALUES(name), " + column + "=VALUES(" + column + ")"
                : "INSERT INTO " + table() + " (uuid, name, first_join, last_join, last_ip, " + column + ") VALUES (?,?,?,?,?,?) "
                + "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, " + column + "=excluded." + column;
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name == null || name.isBlank() ? "-" : name);
            statement.setLong(3, now);
            statement.setLong(4, now);
            statement.setString(5, "");
            statement.setString(6, value);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Aggiornamento " + column + " fallito.", exception);
        }
    }

    private void upsertFlag(UUID uuid, String name, String column, boolean value) {
        long now = System.currentTimeMillis();
        String sql = this.plugin.storage().type().isRemote()
                ? "INSERT INTO " + table() + " (uuid, name, first_join, last_join, last_ip, " + column + ") VALUES (?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE name=VALUES(name), " + column + "=VALUES(" + column + ")"
                : "INSERT INTO " + table() + " (uuid, name, first_join, last_join, last_ip, " + column + ") VALUES (?,?,?,?,?,?) "
                + "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, " + column + "=excluded." + column;
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name == null || name.isBlank() ? "-" : name);
            statement.setLong(3, now);
            statement.setLong(4, now);
            statement.setString(5, "");
            statement.setInt(6, value ? 1 : 0);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Aggiornamento " + column + " fallito.", exception);
        }
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
