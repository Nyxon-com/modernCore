package com.valaryrpg.core.module.moderation;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.storage.StorageException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PunishmentRepository {

    private final ValaryCorePlugin plugin;

    public PunishmentRepository(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void insert(PunishmentType type, UUID uuid, String name, String ip, String staff, String reason, Long expiresAt) {
        String sql = "INSERT INTO " + table() + " (type, target_uuid, target_name, target_ip, staff, reason, created_at, expires_at, active) "
                + "VALUES (?,?,?,?,?,?,?,?,1)";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            if (uuid == null) {
                statement.setNull(2, Types.VARCHAR);
            } else {
                statement.setString(2, uuid.toString());
            }
            statement.setString(3, name);
            statement.setString(4, ip == null ? "" : ip);
            statement.setString(5, staff);
            statement.setString(6, reason);
            statement.setLong(7, System.currentTimeMillis());
            if (expiresAt == null) {
                statement.setNull(8, Types.BIGINT);
            } else {
                statement.setLong(8, expiresAt);
            }
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Inserimento punizione fallito.", exception);
        }
    }

    public Optional<Punishment> active(PunishmentType type, UUID uuid, String name, String ip) {
        String sql = "SELECT id, type, target_uuid, target_name, target_ip, staff, reason, created_at, expires_at, active FROM "
                + table() + " WHERE active = 1 AND type = ? AND ("
                + "(target_uuid IS NOT NULL AND target_uuid = ?) OR lower(target_name) = lower(?) OR (target_ip <> '' AND target_ip = ?)"
                + ") ORDER BY id DESC";
        List<Long> expired = new ArrayList<>();
        Optional<Punishment> found = Optional.empty();
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setString(2, uuid == null ? "" : uuid.toString());
            statement.setString(3, name == null ? "" : name);
            statement.setString(4, ip == null ? "" : ip);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Punishment punishment = read(result);
                    if (punishment.expired()) {
                        expired.add(punishment.id());
                        continue;
                    }
                    found = Optional.of(punishment);
                    break;
                }
            }
        } catch (SQLException exception) {
            throw new StorageException("Lettura punizione fallita.", exception);
        }
        deactivateAll(expired);
        return found;
    }

    public List<Punishment> listActive(PunishmentType type) {
        String sql = "SELECT id, type, target_uuid, target_name, target_ip, staff, reason, created_at, expires_at, active FROM "
                + table() + " WHERE active = 1 AND type = ? ORDER BY id DESC";
        List<Punishment> list = new ArrayList<>();
        List<Long> expired = new ArrayList<>();
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Punishment punishment = read(result);
                    if (punishment.expired()) {
                        expired.add(punishment.id());
                        continue;
                    }
                    list.add(punishment);
                }
            }
        } catch (SQLException exception) {
            throw new StorageException("Lista punizioni fallita.", exception);
        }
        deactivateAll(expired);
        return list;
    }

    public List<Punishment> history(UUID uuid, String name, String ip) {
        String sql = "SELECT id, type, target_uuid, target_name, target_ip, staff, reason, created_at, expires_at, active FROM "
                + table() + " WHERE (target_uuid IS NOT NULL AND target_uuid = ?) OR lower(target_name) = lower(?) "
                + "OR (target_ip <> '' AND target_ip = ?) ORDER BY id DESC";
        List<Punishment> list = new ArrayList<>();
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid == null ? "" : uuid.toString());
            statement.setString(2, name == null ? "" : name);
            statement.setString(3, ip == null ? "" : ip);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    list.add(read(result));
                }
            }
        } catch (SQLException exception) {
            throw new StorageException("Storico punizioni fallito.", exception);
        }
        return list;
    }

    public List<Punishment> history(UUID uuid, String name, PunishmentType type) {
        String sql = "SELECT id, type, target_uuid, target_name, target_ip, staff, reason, created_at, expires_at, active FROM "
                + table() + " WHERE type = ? AND ((target_uuid IS NOT NULL AND target_uuid = ?) OR lower(target_name) = lower(?)) ORDER BY id DESC";
        List<Punishment> list = new ArrayList<>();
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setString(2, uuid == null ? "" : uuid.toString());
            statement.setString(3, name);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    list.add(read(result));
                }
            }
        } catch (SQLException exception) {
            throw new StorageException("Storico punizioni fallito.", exception);
        }
        return list;
    }

    public int deactivate(PunishmentType type, UUID uuid, String name, String ip) {
        String sql = "UPDATE " + table() + " SET active = 0 WHERE active = 1 AND type = ? AND ("
                + "(target_uuid IS NOT NULL AND target_uuid = ?) OR lower(target_name) = lower(?) OR (target_ip <> '' AND target_ip = ?))";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setString(2, uuid == null ? "" : uuid.toString());
            statement.setString(3, name == null ? "" : name);
            statement.setString(4, ip == null ? "" : ip);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Revoca punizione fallita.", exception);
        }
    }

    public void deactivate(long id) {
        deactivateAll(List.of(id));
    }

    private void deactivateAll(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String sql = "UPDATE " + table() + " SET active = 0 WHERE id = ?";
        try (Connection connection = this.plugin.storage().connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Long id : ids) {
                statement.setLong(1, id);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) {
            throw new StorageException("Revoca punizione fallita.", exception);
        }
    }

    private Punishment read(ResultSet result) throws SQLException {
        String uuidRaw = result.getString("target_uuid");
        long expires = result.getLong("expires_at");
        boolean expiresNull = result.wasNull();
        return new Punishment(
                result.getLong("id"),
                PunishmentType.valueOf(result.getString("type")),
                uuidRaw == null ? null : UUID.fromString(uuidRaw),
                result.getString("target_name"),
                result.getString("target_ip"),
                result.getString("staff"),
                result.getString("reason"),
                result.getLong("created_at"),
                expiresNull ? null : expires,
                result.getInt("active") == 1
        );
    }

    private String table() {
        return this.plugin.storage().table("punishments");
    }
}
