//made with love by nyxon

package com.modern.core.module.moderation;

import com.modern.core.ModernCorePlugin;
import com.modern.core.storage.StorageException;

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

  private final ModernCorePlugin plugin;

  public PunishmentRepository(ModernCorePlugin plugin) {
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
    String sql = select() + " WHERE active = 1 AND type = ? AND " + matchSql(type) + " ORDER BY id DESC";
    List<Long> expired = new ArrayList<>();
    Optional<Punishment> found = Optional.empty();
    try (Connection connection = this.plugin.storage().connection();
       PreparedStatement statement = connection.prepareStatement(sql)) {
      bindMatch(statement, 1, type, uuid, name, ip);
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
    String sql = "SELECT id, type, target_uuid, target_name, target_ip, staff, reason, created_at, expires_at, active, revoked_at, revoked_by, revoke_reason FROM "
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
    return history(uuid, name, ip, false);
  }

  public List<Punishment> historyByIp(String ip) {
    return history(null, null, ip, true);
  }

  public List<Punishment> history(UUID uuid, String name, String ip, boolean byIp) {
    String sql;
    if (byIp) {
      sql = select() + " WHERE target_ip <> '' AND target_ip = ? ORDER BY id DESC";
    } else {
      sql = select() + " WHERE (target_uuid IS NOT NULL AND target_uuid = ?) OR lower(target_name) = lower(?) ORDER BY id DESC";
    }
    List<Punishment> list = new ArrayList<>();
    try (Connection connection = this.plugin.storage().connection();
       PreparedStatement statement = connection.prepareStatement(sql)) {
      if (byIp) {
        statement.setString(1, ip == null ? "" : ip);
      } else {
        statement.setString(1, uuid == null ? "" : uuid.toString());
        statement.setString(2, name == null ? "" : name);
      }
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
    String sql = select() + " WHERE type = ? AND ((target_uuid IS NOT NULL AND target_uuid = ?) OR lower(target_name) = lower(?)) ORDER BY id DESC";
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
    String sql = "UPDATE " + table() + " SET active = 0 WHERE active = 1 AND type = ? AND " + matchSql(type);
    try (Connection connection = this.plugin.storage().connection();
       PreparedStatement statement = connection.prepareStatement(sql)) {
      bindMatch(statement, 1, type, uuid, name, ip);
      return statement.executeUpdate();
    } catch (SQLException exception) {
      throw new StorageException("Revoca punizione fallita.", exception);
    }
  }

  public int pardon(PunishmentType type, UUID uuid, String name, String ip, String staff, String reason) {
    String sql = "UPDATE " + table() + " SET active = 0, revoked_at = ?, revoked_by = ?, revoke_reason = ? "
        + "WHERE active = 1 AND type = ? AND " + matchSql(type);
    try (Connection connection = this.plugin.storage().connection();
       PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, System.currentTimeMillis());
      statement.setString(2, staff);
      statement.setString(3, reason);
      bindMatch(statement, 4, type, uuid, name, ip);
      return statement.executeUpdate();
    } catch (SQLException exception) {
      throw new StorageException("Pardon punizione fallito.", exception);
    }
  }

  public List<Punishment> listPardons() {
    String sql = select() + " WHERE revoked_at IS NOT NULL AND type IN ('BAN','IPBAN') ORDER BY revoked_at DESC";
    List<Punishment> list = new ArrayList<>();
    try (Connection connection = this.plugin.storage().connection();
       PreparedStatement statement = connection.prepareStatement(sql);
       ResultSet result = statement.executeQuery()) {
      while (result.next()) {
        list.add(read(result));
      }
    } catch (SQLException exception) {
      throw new StorageException("Lista pardon fallita.", exception);
    }
    return list;
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
    long revoked = result.getLong("revoked_at");
    boolean revokedNull = result.wasNull();
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
        result.getInt("active") == 1,
        revokedNull ? null : revoked,
        empty(result.getString("revoked_by")),
        empty(result.getString("revoke_reason"))
    );
  }

  private static String empty(String value) {
    return value == null ? "" : value;
  }

  private static boolean matchIp(PunishmentType type) {
    return type == PunishmentType.IPBAN;
  }

  private static String matchSql(PunishmentType type) {
    if (matchIp(type)) {
      return "((target_uuid IS NOT NULL AND target_uuid = ?) OR lower(target_name) = lower(?) OR (target_ip <> '' AND target_ip = ?))";
    }
    return "((target_uuid IS NOT NULL AND target_uuid = ?) OR lower(target_name) = lower(?))";
  }

  private static void bindMatch(
      PreparedStatement statement,
      int typeIndex,
      PunishmentType type,
      UUID uuid,
      String name,
      String ip
  ) throws SQLException {
    statement.setString(typeIndex, type.name());
    statement.setString(typeIndex + 1, uuid == null ? "" : uuid.toString());
    statement.setString(typeIndex + 2, name == null ? "" : name);
    if (matchIp(type)) {
      statement.setString(typeIndex + 3, ip == null ? "" : ip);
    }
  }

  private String select() {
    return "SELECT id, type, target_uuid, target_name, target_ip, staff, reason, created_at, expires_at, active, revoked_at, revoked_by, revoke_reason FROM "
        + table();
  }

  private String table() {
    return this.plugin.storage().table("punishments");
  }
}
