package com.valaryrpg.core.storage;

import java.util.Locale;

public enum DatabaseType {
    SQLITE,
    MYSQL,
    MARIADB;

    public static DatabaseType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return SQLITE;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "MYSQL" -> MYSQL;
            case "MARIADB" -> MARIADB;
            default -> SQLITE;
        };
    }

    public boolean isRemote() {
        return this == MYSQL || this == MARIADB;
    }
}
