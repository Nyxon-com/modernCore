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
        int despawnSeconds = Math.max(0, this.plugin.configs().mechanics().getInt("ground-items.despawn-seconds", 300));
        if (this.tracked.size() >= this.plugin.configs().mechanics().getInt("ground-items.max-holograms", 80)) {
            return;
        }
        if (despawnSeconds > 0) {
            item.setUnlimitedLifetime(true);
        }
        remove(item.getUniqueId());
        Component text = label(item.getItemStack(), despawnSeconds);
        if (text.equals(Component.empty())) {
            this.tracked.put(item.getUniqueId(), new Tracked(null, System.currentTimeMillis(), despawnSeconds));
            return;
        }
        float scale = (float) this.plugin.configs().mechanics().getDouble("ground-items.scale", 0.8D);
        TextDisplay display = item.getWorld().spawn(item.getLocation(), TextDisplay.class, spawned -> {
            spawned.text(text);
            style(spawned, scale);
        });
        item.addPassenger(display);
        this.tracked.put(item.getUniqueId(), new Tracked(display.getUniqueId(), System.currentTimeMillis(), despawnSeconds));
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
                continue;
            }
            var display = this.plugin.getServer().getEntity(data.displayId);
            if (display instanceof TextDisplay textDisplay) {
                textDisplay.text(label(item.getItemStack(), Math.max(0, remaining)));
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
        float offsetY = (float) yaml.getDouble("ground-items.offset-y", 0.32D);
        display.setTransformation(new Transformation(
                new Vector3f(0f, offsetY, 0f),
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
