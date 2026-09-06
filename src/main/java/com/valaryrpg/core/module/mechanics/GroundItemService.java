package com.valaryrpg.core.module.mechanics;

import com.valaryrpg.core.ValaryCorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.Item;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GroundItemService {

    private final ValaryCorePlugin plugin;
    private final Map<UUID, UUID> holograms = new ConcurrentHashMap<>();

    public GroundItemService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void attach(Item item) {
        if (!this.plugin.configs().mechanics().getBoolean("ground-items.enabled", true)) {
            return;
        }
        Component text = label(item.getItemStack());
        if (text.equals(Component.empty())) {
            return;
        }
        if (this.holograms.size() >= this.plugin.configs().mechanics().getInt("ground-items.max-holograms", 80)) {
            return;
        }
        remove(item.getUniqueId());
        TextDisplay display = item.getWorld().spawn(item.getLocation(), TextDisplay.class, spawned -> {
            spawned.text(text);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setSeeThrough(this.plugin.configs().mechanics().getBoolean("ground-items.see-through", true));
            spawned.setLineWidth(this.plugin.configs().mechanics().getInt("ground-items.line-width", 200));
            spawned.setBackgroundColor(Color.fromARGB(this.plugin.configs().mechanics().getInt("ground-items.background", 1073741824)));
            spawned.setShadowed(false);
            spawned.setPersistent(false);
        });
        item.addPassenger(display);
        this.holograms.put(item.getUniqueId(), display.getUniqueId());
    }

    public void remove(UUID itemId) {
        UUID displayId = this.holograms.remove(itemId);
        if (displayId == null) {
            return;
        }
        var entity = this.plugin.getServer().getEntity(displayId);
        if (entity != null) {
            entity.remove();
        }
    }

    public void clear() {
        for (UUID displayId : List.copyOf(this.holograms.values())) {
            var entity = this.plugin.getServer().getEntity(displayId);
            if (entity != null) {
                entity.remove();
            }
        }
        this.holograms.clear();
    }

    private Component label(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return Component.empty();
        }
        List<String> lines = new ArrayList<>();
        ItemMeta meta = stack.getItemMeta();
        if (this.plugin.configs().mechanics().getBoolean("ground-items.show-name", true)) {
            String name = displayName(stack, meta);
            lines.add(format("ground-items.name-format", Map.of(
                    "name", name,
                    "amount", String.valueOf(stack.getAmount())
            )));
        }
        if (this.plugin.configs().mechanics().getBoolean("ground-items.show-lore", true) && meta != null && meta.hasLore()) {
            List<Component> lore = meta.lore();
            int max = this.plugin.configs().mechanics().getInt("ground-items.max-lore-lines", 4);
            if (lore != null) {
                int shown = 0;
                for (Component line : lore) {
                    if (shown >= max) {
                        break;
                    }
                    String plain = PlainTextComponentSerializer.plainText().serialize(line);
                    if (plain.isBlank()) {
                        continue;
                    }
                    lines.add(format("ground-items.lore-format", Map.of("line", this.plugin.messages().escape(plain))));
                    shown++;
                }
            }
        }
        if (lines.isEmpty()) {
            return Component.empty();
        }
        return this.plugin.messages().deserialize(String.join("<newline>", lines));
    }

    private String displayName(ItemStack stack, ItemMeta meta) {
        if (meta != null && meta.hasDisplayName()) {
            return this.plugin.messages().escape(PlainTextComponentSerializer.plainText().serialize(meta.displayName()));
        }
        String key = stack.getType().translationKey();
        return "<lang:" + key + ">";
    }

    private String format(String path, Map<String, String> placeholders) {
        String raw = this.plugin.configs().mechanics().getString(path, "{name}");
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return raw;
    }
}
