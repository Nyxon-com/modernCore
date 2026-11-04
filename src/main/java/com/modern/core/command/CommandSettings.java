package com.modern.core.command;

import com.modern.core.ModernCorePlugin;

import java.util.List;

public final class CommandSettings {

  private final ModernCorePlugin plugin;

  public CommandSettings(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  public String name(String path, String fallback) {
    String value = this.plugin.configs().root().getString(path, fallback);
    return value == null || value.isBlank() ? fallback : value;
  }

  public List<String> aliases(String path) {
    return this.plugin.configs().root().getStringList(path);
  }
}
