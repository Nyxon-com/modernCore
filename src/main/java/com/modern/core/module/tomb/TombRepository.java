package com.modern.core.module.tomb;

import com.modern.core.ModernCorePlugin;
import com.modern.core.storage.StorageException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class TombRepository {

  private final ModernCorePlugin plugin;

  public TombRepository(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  public long insert(Tomb tomb) {
    String sql = "INSERT INTO " + table()
        + " (uuid, name, created_at, world, x, y, z, cause, killer, inventory, xp) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
    try (Connection connection = this.plugin.storage().connection();
       PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      statement.setString(1, tomb.uuid().toString());
      statement.setString(2, tomb.name());
      statement.setLong(3, tomb.createdAt());
      statement.setString(4, tomb.world());
      statement.setDouble(5, tomb.x());
      statement.setDouble(6, tomb.y());
      statement.setDouble(7, tomb.z());
      statement.setString(8, tomb.cause() == null ? "" : tomb.cause());
      statement.setString(9, tomb.killer() == null ? "" : tomb.killer());
      statement.setString(10, tomb.inventory() == null ? "" : tomb.inventory());
      statement.setInt(11, Math.max(0, tomb.xp()));
      statement.executeUpdate();
      try (ResultSet keys = statement.getGeneratedKeys()) {
        if (keys.next()) {
          return keys.getLong(1);
        }
      }
      return findLatestId(tomb.uuid());
    } catch (SQLException exception) {
      throw new StorageException("Salvataggio tomba fallito.", exception);
    }
  }

  public Optional<Tomb> find(long id) {
    String sql = "SELECT id, uuid, name, created_at, world, x, y, z, cause, killer, inventory, xp FROM "
        + table() + " WHERE id = ?";
    try (Connection connection = this.plugin.storage().connection();
       PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, id);
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          return Optional.empty();
        }
        return Optional.of(map(result));
      }
    } catch (SQLException exception) {
      throw new StorageException("Lettura tomba fallita.", exception);
    }
  }

  public List<Tomb> list(UUID uuid, int limit, int offset) {
    String sql = "SELECT id, uuid, name, created_at, world, x, y, z, cause, killer, inventory, xp FROM "
        + table() + " WHERE uuid = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
    List<Tomb> tombs = new ArrayList<>();
    try (Connection connection = this.plugin.storage().connection();
       PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, uuid.toString());
      statement.setInt(2, Math.max(1, limit));
      statement.setInt(3, Math.max(0, offset));
      try (ResultSet result = statement.executeQuery()) {
        while (result.next()) {
          tombs.add(map(result));
        }
      }
    } catch (SQLException exception) {
      throw new StorageException("Lista tombe fallita.", exception);
    }
    return tombs;
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
      throw new StorageException("Conteggio tombe fallito.", exception);
    }
  }

  public void delete(long id) {
    String sql = "DELETE FROM " + table() + " WHERE id = ?";
    try (Connection connection = this.plugin.storage().connection();
       PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, id);
      statement.executeUpdate();
    } catch (SQLException exception) {
      throw new StorageException("Cancellazione tomba fallita.", exception);
    }
  }

  public void trim(UUID uuid, int keep) {
    if (keep < 0) {
      return;
    }
    int total = count(uuid);
    int remove = total - keep;
    if (remove <= 0) {
      return;
    }
    String sql = this.plugin.storage().type().isRemote()
        ? "DELETE FROM " + table() + " WHERE uuid = ? ORDER BY created_at ASC LIMIT ?"
        : "DELETE FROM " + table() + " WHERE id IN (SELECT id FROM " + table()
        + " WHERE uuid = ? ORDER BY created_at ASC LIMIT ?)";
    try (Connection connection = this.plugin.storage().connection();
       PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, uuid.toString());
      statement.setInt(2, remove);
      statement.executeUpdate();
    } catch (SQLException exception) {
      throw new StorageException("Trim tombe fallito.", exception);
    }
  }

  private long findLatestId(UUID uuid) throws SQLException {
    String sql = "SELECT id FROM " + table() + " WHERE uuid = ? ORDER BY created_at DESC LIMIT 1";
    try (Connection connection = this.plugin.storage().connection();
       PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, uuid.toString());
      try (ResultSet result = statement.executeQuery()) {
        return result.next() ? result.getLong(1) : 0L;
      }
    }
  }

  private static Tomb map(ResultSet result) throws SQLException {
    return new Tomb(
        result.getLong("id"),
        UUID.fromString(result.getString("uuid")),
        result.getString("name"),
        result.getLong("created_at"),
        result.getString("world"),
        result.getDouble("x"),
        result.getDouble("y"),
        result.getDouble("z"),
        empty(result.getString("cause")),
        empty(result.getString("killer")),
        empty(result.getString("inventory")),
        result.getInt("xp")
    );
  }

  private static String empty(String value) {
    return value == null ? "" : value;
  }

  private String table() {
    return this.plugin.storage().table("tombs");
  }
}
