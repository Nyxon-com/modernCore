package com.modern.core.module.homes;

import com.modern.core.ModernCorePlugin;
import com.modern.core.storage.StoredLocation;
import com.modern.core.storage.StorageException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistenza home su SQL (tabella prefix + homes).
 */
public final class HomeRepository {

    private final ModernCorePlugin plugin;

    public HomeRepository(ModernCorePlugin plugin) {
        this.plugin = plugin;
    }

    public List<StoredLocation> list(UUID uuid) {
        String sql = "SELECT name, world, x, y, z, yaw, pitch FROM " + table() + " WHERE uuid = ? ORDER BY name";
        List<StoredLocation> homes = new ArrayList<>();
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    homes.add(read(uuid, result));
                }
            }
        } catch (SQLException exception) {
            throw new StorageException("Lettura home fallita.", exception);
        }
        return homes;
    }

    public Optional<StoredLocation> find(UUID uuid, String name) {
        String sql = "SELECT name, world, x, y, z, yaw, pitch FROM " + table() + " WHERE uuid = ? AND name = ?";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(read(uuid, result));
                }
            }
        } catch (SQLException exception) {
            throw new StorageException("Ricerca home fallita.", exception);
        }
        return Optional.empty();
    }

    public int count(UUID uuid) {
        String sql = "SELECT COUNT(*) FROM " + table() + " WHERE uuid = ?";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            throw new StorageException("Conteggio home fallito.", exception);
        }
    }

    public void upsert(StoredLocation home) {
        String sql = this.plugin.storage().type().isRemote()
                ? "INSERT INTO " + table() + " (uuid, name, world, x, y, z, yaw, pitch) VALUES (?,?,?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch)"
                : "INSERT INTO " + table() + " (uuid, name, world, x, y, z, yaw, pitch) VALUES (?,?,?,?,?,?,?,?) "
                + "ON CONFLICT(uuid, name) DO UPDATE SET world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z, yaw=excluded.yaw, pitch=excluded.pitch";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, home.owner().toString());
            statement.setString(2, home.name());
            statement.setString(3, home.worldName());
            statement.setDouble(4, home.x());
            statement.setDouble(5, home.y());
            statement.setDouble(6, home.z());
            statement.setFloat(7, home.yaw());
            statement.setFloat(8, home.pitch());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Salvataggio home fallito.", exception);
        }
    }

    public boolean delete(UUID uuid, String name) {
        String sql = "DELETE FROM " + table() + " WHERE uuid = ? AND name = ?";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new StorageException("Cancellazione home fallita.", exception);
        }
    }

    private String table() {
        return this.plugin.storage().table("homes");
    }

    private static StoredLocation read(UUID uuid, ResultSet result) throws SQLException {
        return new StoredLocation(
                uuid,
                result.getString("name"),
                result.getString("world"),
                result.getDouble("x"),
                result.getDouble("y"),
                result.getDouble("z"),
                result.getFloat("yaw"),
                result.getFloat("pitch")
        );
    }
}
