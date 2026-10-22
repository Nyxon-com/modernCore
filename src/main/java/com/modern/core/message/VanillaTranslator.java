//made with love by nyxon

package com.modern.core.message;

import com.modern.core.ModernCorePlugin;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.Translator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.Locale;

public final class VanillaTranslator implements Translator {

  private final ModernCorePlugin plugin;
  private final Key name = Key.key("valaryrpg", "vanilla");
  private final MiniMessage miniMessage = MiniMessage.miniMessage();
  private boolean registered;

  public VanillaTranslator(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  public void sync() {
    boolean enabled = this.plugin.configs().root().getBoolean("plugin.vanilla-messages", true);
    if (enabled && !this.registered) {
      GlobalTranslator.translator().addSource(this);
      this.registered = true;
      return;
    }
    if (!enabled && this.registered) {
      GlobalTranslator.translator().removeSource(this);
      this.registered = false;
    }
  }

  public void unregister() {
    if (this.registered) {
      GlobalTranslator.translator().removeSource(this);
      this.registered = false;
    }
  }

  @Override
  public @NotNull Key name() {
    return this.name;
  }

  @Override
  public @Nullable MessageFormat translate(@NotNull String key, @NotNull Locale locale) {
    return null;
  }

  @Override
  public @Nullable Component translate(@NotNull TranslatableComponent component, @NotNull Locale locale) {
    if (!this.plugin.configs().root().getBoolean("plugin.vanilla-messages", true)) {
      return null;
    }
    String template = this.plugin.configs().vanilla().getString(component.key());
    if (template == null) {
      return null;
    }
    if (template.isBlank()) {
      return Component.empty();
    }
    String prefix = this.plugin.configs().root().getString("plugin.prefix", "");
    String filled = template.replace("{prefix}", prefix);
    for (int i = 0; i < component.arguments().size(); i++) {
      Component argument = component.arguments().get(i).asComponent();
      filled = filled.replace("{" + i + "}", this.miniMessage.serialize(argument));
    }
    return this.miniMessage.deserialize(filled);
  }
}
