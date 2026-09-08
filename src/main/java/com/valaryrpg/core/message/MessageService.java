package com.valaryrpg.core.message;

import com.valaryrpg.core.ValaryCorePlugin;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Map;

/**
 * Messaggi MiniMessage da messages.yml. Placeholder {chiave} sostituiti prima del parse.
 */
public final class MessageService {

    private final ValaryCorePlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MessageService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        // I testi arrivano già da ConfigManager.loadAll(); questo metodo esiste per chiarezza API.
    }

    public void send(Audience audience, String key) {
        audience.sendMessage(component(key, Map.of()));
    }

    public void send(Audience audience, String key, Map<String, String> placeholders) {
        audience.sendMessage(component(key, placeholders));
    }

    public Component deserialize(String miniMessage) {
        return this.miniMessage.deserialize(miniMessage == null ? "" : miniMessage);
    }

    public String escape(String text) {
        return this.miniMessage.escapeTags(text == null ? "" : text);
    }

    public String text(String key) {
        return text(key, Map.of());
    }

    public String text(String key, Map<String, String> placeholders) {
        String raw = this.plugin.configs().messages().getString(key, "<red>Messaggio mancante: " + key);
        String prefix = this.plugin.configs().root().getString("plugin.prefix", "");
        raw = raw.replace("{prefix}", prefix);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return raw;
    }

    public Component component(String key, Map<String, String> placeholders) {
        return this.miniMessage.deserialize(text(key, placeholders));
    }
}
