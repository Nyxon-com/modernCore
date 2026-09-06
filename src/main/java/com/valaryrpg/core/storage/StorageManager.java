package com.valaryrpg.core.storage;

import com.valaryrpg.core.ValaryCorePlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Pool HikariCP verso SQLite, MySQL o MariaDB. Lo schema viene migrato all'avvio.
 */
public final class StorageManager {

    private final ValaryCorePlugin plugin;
    private HikariDataSource dataSource;
    private DatabaseType type;
    private String tablePrefix;

    public StorageManager(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        FileConfiguration yaml = this.plugin.configs().storage();
        this.type = DatabaseType.from(yaml.getString("storage.type", "SQLITE"));
        this.tablePrefix = yaml.getString("storage.table-prefix", "valary_");

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("ValaryRPG-Core");
        hikari.setConnectionTimeout(yaml.getLong("storage.pool.connection-timeout", 30_000L));
        hikari.setIdleTimeout(yaml.getLong("storage.pool.idle-timeout", 600_000L));
        hikari.setMaxLifetime(yaml.getLong("storage.pool.max-lifetime", 1_800_000L));
        hikari.setKeepaliveTime(yaml.getLong("storage.pool.keepalive-time", 0L));

        if (this.type == DatabaseType.SQLITE) {
            configureSqlite(hikari, yaml);
        } else {
            configureRemote(hikari, yaml);
        }

        this.dataSource = new HikariDataSource(hikari);
        migrate();
        this.plugin.getLogger().info("Storage connesso (" + this.type + ").");
    }

    public Connection connection() throws SQLException {
        if (this.dataSource == null) {
            throw new SQLException("Storage non inizializzato.");
        }
        return this.dataSource.getConnection();
    }

    public String table(String name) {
        return this.tablePrefix + name;
    }

    public DatabaseType type() {
        return this.type;
    }

    public void close() {
        if (this.dataSource != null && !this.dataSource.isClosed()) {
            this.dataSource.close();
        }
    }

    private void configureSqlite(HikariConfig hikari, FileConfiguration yaml) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new StorageException("Driver SQLite assente nel JAR.", exception);
        }
        File database = new File(this.plugin.getDataFolder(), yaml.getString("storage.sqlite.file", "data.db"));
        database.getParentFile().mkdirs();
        hikari.setJdbcUrl("jdbc:sqlite:" + database.getAbsolutePath());
        hikari.setMaximumPoolSize(1);
        hikari.setMinimumIdle(1);
        hikari.setConnectionTestQuery("SELECT 1");
    }

    private void configureRemote(HikariConfig hikari, FileConfiguration yaml) {
        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException exception) {
            throw new StorageException("Driver MariaDB/MySQL assente nel JAR.", exception);
        }

        String host = yaml.getString("storage.mysql.host", "127.0.0.1");
        int port = yaml.getInt("storage.mysql.port", 3306);
        String database = yaml.getString("storage.mysql.database", "valarycore");
        String username = yaml.getString("storage.mysql.username", "root");
        String password = yaml.getString("storage.mysql.password", "");
        boolean useSsl = yaml.getBoolean("storage.mysql.use-ssl", false);

        String scheme = this.type == DatabaseType.MYSQL ? "jdbc:mysql://" : "jdbc:mariadb://";
        hikari.setJdbcUrl(scheme + host + ":" + port + "/" + database
                + "?useSSL=" + useSsl
                + "&allowPublicKeyRetrieval=true"
                + "&characterEncoding=utf8");
        hikari.setUsername(username);
        hikari.setPassword(password);
        hikari.setMaximumPoolSize(yaml.getInt("storage.pool.maximum-pool-size", 10));
        hikari.setMinimumIdle(yaml.getInt("storage.pool.minimum-idle", 2));
        hikari.setConnectionTestQuery("SELECT 1");
    }

    private void migrate() {
        String meta = table("meta");
        String create = "CREATE TABLE IF NOT EXISTS " + meta + " ("
                + "k VARCHAR(64) NOT NULL PRIMARY KEY,"
                + "v VARCHAR(255) NOT NULL"
                + ")";
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute(create);
        } catch (SQLException exception) {
            throw new StorageException("Migrazione schema fallita.", exception);
        }

        String upsert = this.type == DatabaseType.SQLITE
                ? "INSERT OR REPLACE INTO " + meta + " (k, v) VALUES (?, ?)"
                : "INSERT INTO " + meta + " (k, v) VALUES (?, ?) ON DUPLICATE KEY UPDATE v = VALUES(v)";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, "schema_version");
            statement.setString(2, "1");
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Scrittura versione schema fallita.", exception);
        }
    }
}
