package com.valaryrpg.core.util;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Nella fucina upgrade i nomi item usano small-caps unicode (font custom del pack).
 * Qui li normalizziamo a latino leggibile solo nella GUI della stazione.
 */
public final class UpgradeGuiFontFix implements Listener {

    private static final Map<Character, Character> SMALL_CAPS = Map.ofEntries(
            Map.entry('ᴀ', 'a'), Map.entry('ʙ', 'b'), Map.entry('ᴄ', 'c'), Map.entry('ᴅ', 'd'),
            Map.entry('ᴇ', 'e'), Map.entry('ꜰ', 'f'), Map.entry('ɢ', 'g'), Map.entry('ʜ', 'h'),
            Map.entry('ɪ', 'i'), Map.entry('ᴊ', 'j'), Map.entry('ᴋ', 'k'), Map.entry('ʟ', 'l'),
            Map.entry('ᴍ', 'm'), Map.entry('ɴ', 'n'), Map.entry('ᴏ', 'o'), Map.entry('ᴘ', 'p'),
            Map.entry('ǫ', 'q'), Map.entry('ʀ', 'r'), Map.entry('ꜱ', 's'), Map.entry('ᴛ', 't'),
            Map.entry('ᴜ', 'u'), Map.entry('ᴠ', 'v'), Map.entry('ᴡ', 'w'), Map.entry('x', 'x'),
            Map.entry('ʏ', 'y'), Map.entry('ᴢ', 'z'),
            Map.entry('Ꭺ', 'A'), Map.entry('Ᏼ', 'B')
    );

    private final ValaryCorePlugin plugin;

    public UpgradeGuiFontFix(ValaryCorePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!isUpgradeGui(event.getView())) {
            return;
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> normalize(event.getInventory()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getView() == null || !isUpgradeGui(event.getView())) {
            return;
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> normalize(event.getView().getTopInventory()));
    }

    private static boolean isUpgradeGui(org.bukkit.inventory.InventoryView view) {
        String title = "";
        try {
            Object component = view.getClass().getMethod("title").invoke(view);
            if (component != null) {
                try {
                    Class<?> serializer = Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
                    Object plain = serializer.getMethod("plainText").invoke(null);
                    title = String.valueOf(plain.getClass().getMethod("serialize", Class.forName("net.kyori.adventure.text.Component")).invoke(plain, component));
                } catch (ReflectiveOperationException ignored) {
                    title = String.valueOf(component);
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through
        }
        if (title == null || title.isBlank()) {
            try {
                title = view.getTitle();
            } catch (Throwable ignored) {
                return false;
            }
        }
        String plain = title.replaceAll("§[0-9a-fk-orx]", "");
        String lower = plain.toLowerCase();
        return lower.contains("fucina upgrade") || lower.contains("preview upgrade")
                || lower.contains("upgrade marte");
    }

    private static void normalize(Inventory inventory) {
        if (inventory == null) {
            return;
        }
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            boolean changed = false;
            if (meta.hasDisplayName()) {
                String next = toLatin(meta.getDisplayName());
                if (!next.equals(meta.getDisplayName())) {
                    meta.setDisplayName(next);
                    changed = true;
                }
            }
            if (meta.hasLore()) {
                List<String> lore = meta.getLore();
                if (lore != null) {
                    List<String> nextLore = new ArrayList<>(lore.size());
                    boolean loreChanged = false;
                    for (String line : lore) {
                        String converted = toLatin(line);
                        nextLore.add(converted);
                        if (!converted.equals(line)) {
                            loreChanged = true;
                        }
                    }
                    if (loreChanged) {
                        meta.setLore(nextLore);
                        changed = true;
                    }
                }
            }
            if (changed) {
                item.setItemMeta(meta);
                inventory.setItem(i, item);
            }
        }
    }

    static String toLatin(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length());
        boolean changed = false;
        for (int i = 0; i < input.length(); ) {
            int cp = input.codePointAt(i);
            i += Character.charCount(cp);
            if (cp <= 0xFFFF) {
                char ch = (char) cp;
                Character mapped = SMALL_CAPS.get(ch);
                if (mapped != null) {
                    out.append(mapped);
                    changed = true;
                    continue;
                }
            }
            out.appendCodePoint(cp);
        }
        if (!changed) {
            return input;
        }
        // Title-case-ish: first letter of words uppercase for readability on names
        return out.toString();
    }
}
