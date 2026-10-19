package com.modern.core.command;

import com.modern.core.ModernCorePlugin;

import java.util.List;

/**
 * Nomi e alias dei comandi letti da config.yml (commands.*).
 */
final class CommandSettings {

    private final ModernCorePlugin plugin;

    CommandSettings(ModernCorePlugin plugin) {
        this.plugin = plugin;
    }

    String name(String path, String fallback) {
        String value = this.plugin.configs().root().getString(path, fallback);
        return value == null || value.isBlank() ? fallback : value;
    }

    List<String> aliases(String path) {
        return this.plugin.configs().root().getStringList(path);
    }
}
