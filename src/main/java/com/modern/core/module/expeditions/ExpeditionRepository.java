package com.modern.core.module.expeditions;

import com.modern.core.ModernCorePlugin;
import com.modern.core.storage.StorageException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

final class ExpeditionRepository {

  record Run(String expedition, long startedAt, long endsAt) {
  }

  private final ModernCorePlugin plugin;

  ExpeditionRepository(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  void createTable() {
    try (Connection connection = this.plugin.storage().connection();
       Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE IF NOT EXISTS " + table() + " ("
          + "uuid VARCHAR(36) NOT NULL PRIMARY KEY,"
          + "expedition VARCHAR(32) NOT NULL,"
          + "started_at BIGINT NOT NULL,"
          + "ends_at BIGINT NOT NULL"
          + ")");
    } catch (SQLException exception) {
      throw new StorageException("Creazione tabella spedizioni fallita.", exception);
    }
  }

  Optional<Run> find(UUID uuid) {
    String sql = "SELECT expedition, started_at, ends_at FROM " + table() + " WHERE uuid = ?";
    try (Connection connection = this.plugin.storage().connection();
       PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, uuid.toString());
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          return Optional.empty();
        }
        return Optional.of(new Run(
            result.getString("expedition"),
            result.getLong("started_at"),
            result.getLong("ends_at")
        ));
      }
    } catch (SQLException exception) {
      throw new StorageException("Lettura spedizione fallita.", exception);
    }
  }

  boolean insert(UUID uuid, Run run) {
    String verb = this.plugin.storage().type().isRemote() ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ";
    String sql = verb + table() + " (uuid, expedition, started_at, ends_at) VALUES (?,?,?,?)";
    try (Connection connection = this.plugin.storage().connection();
       PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, uuid.toString());
      statement.setString(2, run.expedition());
      statement.setLong(3, run.startedAt());
      statement.setLong(4, run.endsAt());
      return statement.executeUpdate() == 1;
    } catch (SQLException exception) {
      throw new StorageException("Avvio spedizione fallito.", exception);
    }
  }

  boolean claim(UUID uuid, Run run, long now) {
    String sql = "DELETE FROM " + table() + " WHERE uuid = ? AND expedition = ? AND ends_at = ? AND ends_at <= ?";
    try (Connection connection = this.plugin.storage().connection();
       PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, uuid.toString());
      statement.setString(2, run.expedition());
      statement.setLong(3, run.endsAt());
      statement.setLong(4, now);
      return statement.executeUpdate() == 1;
    } catch (SQLException exception) {
      throw new StorageException("Riscatto spedizione fallito.", exception);
    }
  }

  boolean finishNow(UUID uuid, long now) {
    String sql = "UPDATE " + table() + " SET ends_at = ? WHERE uuid = ? AND ends_at > ?";
    try (Connection connection = this.plugin.storage().connection();
       PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, now);
      statement.setString(2, uuid.toString());
      statement.setLong(3, now);
      return statement.executeUpdate() == 1;
    } catch (SQLException exception) {
      throw new StorageException("Completamento spedizione fallito.", exception);
    }
  }

  private String table() {
    return this.plugin.storage().table("expeditions");
  }
}
