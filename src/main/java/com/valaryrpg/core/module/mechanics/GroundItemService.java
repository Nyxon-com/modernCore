package com.valaryrpg.core.module.mechanics;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.util.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.Item;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hologram MiniMessage sugli item a terra (non sui give in inventario).
 */
public final class GroundItemService {

    private final ValaryCorePlugin plugin;
    private final Map<UUID, Tracked> tracked = new ConcurrentHashMap<>();
    private BukkitTask task;

    public GroundItemService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!this.plugin.configs().mechanics().getBoolean("ground-items.enabled", true)) {
            return;
        }
        int interval = Math.max(5, this.plugin.configs().mechanics().getInt("ground-items.update-interval-ticks", 20));
        this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, interval, interval);
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    public void attach(Item item) {
        if (!this.plugin.configs().mechanics().getBoolean("ground-items.enabled", true)) {
            return;
        }
        if (item.getPersistentDataContainer().has(Items.noHologramKey(this.plugin), PersistentDataType.BYTE)) {
            return;
        }
        if (this.tracked.containsKey(item.getUniqueId()) && hologramPresent(item.getUniqueId())) {
            return;
        }
        int despawnSeconds = Math.max(0, this.plugin.configs().mechanics().getInt("ground-items.despawn-seconds", 300));
        evictIfNeeded();
        if (despawnSeconds > 0) {
            item.setUnlimitedLifetime(true);
        }
        remove(item.getUniqueId());
        Component text = label(item.getItemStack(), despawnSeconds);
        if (text.equals(Component.empty())) {
            this.tracked.put(item.getUniqueId(), new Tracked(null, System.currentTimeMillis(), despawnSeconds));
            return;
        }
        spawnDisplay(item, text, System.currentTimeMillis(), despawnSeconds);
    }

    public void scanChunk(org.bukkit.Chunk chunk) {
        if (!this.plugin.configs().mechanics().getBoolean("ground-items.enabled", true)) {
            return;
        }
        for (org.bukkit.entity.Entity entity : chunk.getEntities()) {
            if (entity instanceof Item item && item.isValid()) {
                attach(item);
            }
        }
    }

    private boolean hologramPresent(UUID itemId) {
        Tracked found = this.tracked.get(itemId);
        if (found == null || found.displayId == null) {
            return false;
        }
        return this.plugin.getServer().getEntity(found.displayId) instanceof TextDisplay;
    }

    private void evictIfNeeded() {
        int max = this.plugin.configs().mechanics().getInt("ground-items.max-holograms", 400);
        if (max <= 0) {
            return;
        }
        while (this.tracked.size() >= max) {
            UUID oldest = null;
            long oldestAt = Long.MAX_VALUE;
            for (Map.Entry<UUID, Tracked> entry : this.tracked.entrySet()) {
                if (entry.getValue().spawnedAt < oldestAt) {
                    oldestAt = entry.getValue().spawnedAt;
                    oldest = entry.getKey();
                }
            }
            if (oldest == null) {
                return;
            }
            remove(oldest);
        }
    }

    private void spawnDisplay(Item item, Component text, long spawnedAt, int despawnSeconds) {
        float scale = (float) this.plugin.configs().mechanics().getDouble("ground-items.scale", 0.8D);
        // Mai passenger sull'item: in creative il pickup synca male e l'item sembra eliminato.
        TextDisplay display = item.getWorld().spawn(labelLocation(item), TextDisplay.class, spawned -> {
            spawned.text(text);
            style(spawned, scale);
        });
        this.tracked.put(item.getUniqueId(), new Tracked(display.getUniqueId(), spawnedAt, despawnSeconds));
    }

    private org.bukkit.Location labelLocation(Item item) {
        double offsetY = this.plugin.configs().mechanics().getDouble("ground-items.offset-y", 0.32D);
        return item.getLocation().clone().add(0.0D, offsetY, 0.0D);
    }

    public void remove(UUID itemId) {
        Tracked found = this.tracked.remove(itemId);
        if (found == null || found.displayId == null) {
            return;
        }
        var entity = this.plugin.getServer().getEntity(found.displayId);
        if (entity != null) {
            entity.remove();
        }
    }

    public void clear() {
        stop();
        for (Map.Entry<UUID, Tracked> entry : List.copyOf(this.tracked.entrySet())) {
            var entity = this.plugin.getServer().getEntity(entry.getKey());
            if (entity instanceof Item item) {
                item.setUnlimitedLifetime(false);
            }
            if (entry.getValue().displayId != null) {
                var display = this.plugin.getServer().getEntity(entry.getValue().displayId);
                if (display != null) {
                    display.remove();
                }
            }
        }
        this.tracked.clear();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Tracked> entry : List.copyOf(this.tracked.entrySet())) {
            var entity = this.plugin.getServer().getEntity(entry.getKey());
            if (!(entity instanceof Item item) || !item.isValid()) {
                remove(entry.getKey());
                continue;
            }
            Tracked data = entry.getValue();
            int remaining = data.despawnSeconds <= 0
                    ? -1
                    : data.despawnSeconds - (int) ((now - data.spawnedAt) / 1000L);
            if (data.despawnSeconds > 0 && remaining <= 0) {
                remove(entry.getKey());
                item.remove();
                continue;
            }
            if (data.displayId == null) {
                Component text = label(item.getItemStack(), Math.max(0, remaining));
                if (!text.equals(Component.empty())) {
                    spawnDisplay(item, text, data.spawnedAt, data.despawnSeconds);
                }
                continue;
            }
            var display = this.plugin.getServer().getEntity(data.displayId);
            if (display instanceof TextDisplay textDisplay) {
                textDisplay.text(label(item.getItemStack(), Math.max(0, remaining)));
                textDisplay.teleportAsync(labelLocation(item));
            } else {
                remove(entry.getKey());
                Component text = label(item.getItemStack(), Math.max(0, remaining));
                if (!text.equals(Component.empty())) {
                    spawnDisplay(item, text, data.spawnedAt, data.despawnSeconds);
                }
            }
        }
    }

    private void style(TextDisplay display, float scale) {
        var yaml = this.plugin.configs().mechanics();
        display.setBillboard(billboard(yaml.getString("ground-items.billboard", "CENTER")));
        display.setSeeThrough(yaml.getBoolean("ground-items.see-through", false));
        display.setLineWidth(yaml.getInt("ground-items.line-width", 140));
        display.setDefaultBackground(yaml.getBoolean("ground-items.default-background", false));
        display.setBackgroundColor(Color.fromARGB(yaml.getInt("ground-items.background", 0)));
        display.setShadowed(yaml.getBoolean("ground-items.shadowed", true));
        display.setPersistent(false);
        display.setTextOpacity((byte) yaml.getInt("ground-items.text-opacity", -1));
        display.setAlignment(alignment(yaml.getString("ground-items.alignment", "CENTER")));
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f(scale, scale, scale),
                new Quaternionf()
        ));
    }

    private Component label(ItemStack stack, int remainingSeconds) {
        if (stack == null || stack.getType().isAir()) {
            return Component.empty();
        }
        List<String> lines = new ArrayList<>();
        ItemMeta meta = stack.getItemMeta();
        String font = this.plugin.configs().mechanics().getString("ground-items.font", "");
        String fontTag = font == null || font.isBlank() ? "" : "<font:" + font + ">";
        String fontEnd = fontTag.isEmpty() ? "" : "</font>";
        if (this.plugin.configs().mechanics().getBoolean("ground-items.show-name", true)) {
            lines.add(fontTag + nameLine(stack, meta) + fontEnd);
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
                    lines.add(fontTag + format("ground-items.lore-format", Map.of("line", this.plugin.messages().escape(plain))) + fontEnd);
                    shown++;
                }
            }
        }
        if (this.plugin.configs().mechanics().getBoolean("ground-items.show-despawn", true) && remainingSeconds >= 0) {
            lines.add(fontTag + format("ground-items.despawn-format", Map.of("seconds", String.valueOf(remainingSeconds))) + fontEnd);
        }
        if (lines.isEmpty()) {
            return Component.empty();
        }
        return this.plugin.messages().deserialize(String.join("<newline>", lines));
    }

    private String nameLine(ItemStack stack, ItemMeta meta) {
        boolean custom = meta != null && meta.hasDisplayName();
        String name = custom
                ? MiniMessage.miniMessage().serialize(meta.displayName())
                : format("ground-items.vanilla-name", Map.of("name", "<lang:" + stack.getType().translationKey() + ">"));
        boolean hideAmount = stack.getAmount() == 1
                && this.plugin.configs().mechanics().getBoolean("ground-items.hide-amount-when-one", true);
        String path = hideAmount ? "ground-items.name-format-one" : "ground-items.name-format";
        return format(path, Map.of(
                "name", name,
                "amount", String.valueOf(stack.getAmount())
        ));
    }

    private String format(String path, Map<String, String> placeholders) {
        String raw = this.plugin.configs().mechanics().getString(path, "{name}");
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return raw;
    }

    private static Display.Billboard billboard(String raw) {
        try {
            return Display.Billboard.valueOf((raw == null ? "CENTER" : raw).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Display.Billboard.CENTER;
        }
    }

    private static TextDisplay.TextAlignment alignment(String raw) {
        try {
            return TextDisplay.TextAlignment.valueOf((raw == null ? "CENTER" : raw).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return TextDisplay.TextAlignment.CENTER;
        }
    }

    private record Tracked(UUID displayId, long spawnedAt, int despawnSeconds) {
    }
}
