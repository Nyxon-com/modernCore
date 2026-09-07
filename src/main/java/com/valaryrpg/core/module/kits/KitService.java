package com.valaryrpg.core.module.kits;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.gui.GuiLayouts;
import com.valaryrpg.core.gui.MenuHolder;
import com.valaryrpg.core.gui.PanelClicks;
import com.valaryrpg.core.gui.PanelItems;
import com.valaryrpg.core.gui.Panels;
import com.valaryrpg.core.util.MmoItems;
import com.valaryrpg.core.util.Perms;
import com.valaryrpg.core.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Claim kit da GUI o comando, con cooldown SQL, one-time, pagine e anteprima.
 */
public final class KitService {

    public static final String MENU = "kits";
    public static final String PREVIEW = "kit-preview";

    private final ValaryCorePlugin plugin;
    private final KitClaimRepository claims;
    private final Set<UUID> claiming = ConcurrentHashMap.newKeySet();

    public KitService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
        this.claims = new KitClaimRepository(plugin);
    }

    public void open(Player player) {
        open(player, 1);
    }

    public void open(Player player, int page) {
        ConfigurationSection root = this.plugin.configs().kits().getConfigurationSection("gui");
        ConfigurationSection layout = pageSection(root, page);
        if (layout == null) {
            this.plugin.messages().send(player, "kits.no-page", Map.of("page", String.valueOf(page)));
            return;
        }
        Panels.open(
                this.plugin,
                player,
                new MenuHolder(MENU, page, ""),
                layout,
                root,
                Map.of("page", String.valueOf(page))
        );
    }

    public void click(Player player, int slot, int page) {
        ConfigurationSection layout = pageSection(this.plugin.configs().kits().getConfigurationSection("gui"), page);
        ConfigurationSection button = GuiLayouts.button(layout, slot);
        if (button == null) {
            return;
        }
        String action = GuiLayouts.action(button);
        PanelClicks.run(this.plugin, player, button);
        if (action.equals("kit")) {
            String kitId = button.getString("kit", "");
            if (kitId != null && !kitId.isBlank()) {
                claim(player, kitId, false);
            }
            return;
        }
        if (action.equals("preview")) {
            String kitId = button.getString("kit", "");
            if (kitId != null && !kitId.isBlank()) {
                preview(player, kitId);
            }
            return;
        }
        if (action.equals("page")) {
            open(player, button.getInt("page", 1));
            return;
        }
        if (action.equals("close")) {
            player.closeInventory();
        }
    }

    public void preview(Player player, String kitId) {
        ConfigurationSection kit = this.plugin.configs().kits().getConfigurationSection("kits." + kitId.toLowerCase(Locale.ROOT));
        if (kit == null) {
            this.plugin.messages().send(player, "kits.unknown", Map.of("kit", kitId));
            return;
        }
        ConfigurationSection preview = this.plugin.configs().kits().getConfigurationSection("preview");
        int rows = GuiLayouts.rows(preview, 6);
        String titleRaw = preview == null ? "" : preview.getString("title", "");
        net.kyori.adventure.text.Component title;
        if (titleRaw == null || titleRaw.isBlank()) {
            title = this.plugin.messages().component("kits.preview-title", Map.of("kit", kitId));
        } else {
            title = this.plugin.messages().deserialize(titleRaw.replace("{kit}", kitId).replace("{player}", player.getName()));
        }
        MenuHolder holder = new MenuHolder(PREVIEW, 1, kitId);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, title);
        holder.inventory(inventory);
        int slot = 0;
        for (Map<?, ?> raw : kit.getMapList("items")) {
            ItemStack stack = previewItem(raw);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (slot >= inventory.getSize()) {
                break;
            }
            inventory.setItem(slot, stack);
            slot++;
        }
        int closeSlot = preview == null ? inventory.getSize() - 1 : preview.getInt("close-slot", inventory.getSize() - 1);
        if (closeSlot >= 0 && closeSlot < inventory.getSize()) {
            ConfigurationSection close = preview == null ? null : preview.getConfigurationSection("close");
            inventory.setItem(closeSlot, close == null
                    ? named(Material.BARRIER, "<red>Chiudi")
                    : PanelItems.from(this.plugin, close, player));
        }
        player.openInventory(inventory);
    }

    public boolean closePreviewSlot(int slot) {
        ConfigurationSection preview = this.plugin.configs().kits().getConfigurationSection("preview");
        int close = preview == null ? 53 : preview.getInt("close-slot", 53);
        return slot == close;
    }

    public void claim(CommandSender staff, Player target, String kitId, boolean ignoreCooldown) {
        ConfigurationSection kit = this.plugin.configs().kits().getConfigurationSection("kits." + kitId.toLowerCase(Locale.ROOT));
        if (kit == null) {
            this.plugin.messages().send(staff, "kits.unknown", Map.of("kit", kitId));
            return;
        }
        String permission = kit.getString("permission", "");
        if (permission != null && !permission.isBlank() && !target.hasPermission(permission)
                && !Perms.has(this.plugin, staff, "kit-bypass")) {
            this.plugin.messages().send(staff, "kits.no-permission", Map.of("kit", kitId));
            return;
        }
        if (this.claiming.contains(target.getUniqueId())) {
            return;
        }
        this.claiming.add(target.getUniqueId());
        UUID uuid = target.getUniqueId();
        String id = kitId.toLowerCase(Locale.ROOT);
        long cooldownMs = kit.getLong("cooldown-seconds", 0L) * 1000L;
        boolean oneTime = kit.getBoolean("one-time", false);
        boolean bypass = ignoreCooldown || Perms.has(this.plugin, target, "kit-bypass") || Perms.has(this.plugin, staff, "kit-bypass");
        Tasks.async(this.plugin, () -> {
            Optional<Long> last = bypass ? Optional.empty() : this.claims.lastClaim(uuid, id);
            if (!bypass && oneTime && last.isPresent()) {
                Tasks.sync(this.plugin, () -> {
                    this.claiming.remove(uuid);
                    this.plugin.messages().send(staff, "kits.one-time", Map.of("kit", id));
                });
                return;
            }
            long now = System.currentTimeMillis();
            if (last.isPresent() && cooldownMs > 0 && now - last.get() < cooldownMs) {
                long left = (cooldownMs - (now - last.get()) + 999) / 1000;
                Tasks.sync(this.plugin, () -> {
                    this.claiming.remove(uuid);
                    this.plugin.messages().send(staff, "kits.cooldown", Map.of(
                            "kit", id,
                            "seconds", String.valueOf(left)
                    ));
                });
                return;
            }
            this.claims.mark(uuid, id, now);
            Tasks.sync(this.plugin, () -> {
                this.claiming.remove(uuid);
                if (!target.isOnline()) {
                    return;
                }
                give(target, kit);
                this.plugin.messages().send(target, "kits.received", Map.of("kit", id));
                if (staff != target) {
                    this.plugin.messages().send(staff, "kits.given", Map.of("kit", id, "player", target.getName()));
                }
            });
        });
    }

    public void claim(Player player, String kitId, boolean ignoreCooldown) {
        claim(player, player, kitId, ignoreCooldown);
    }

    public void firstJoin(Player player) {
        String kit = this.plugin.configs().kits().getString("settings.first-join-kit", "");
        if (kit == null || kit.isBlank()) {
            return;
        }
        int delay = Math.max(1, this.plugin.configs().kits().getInt("settings.first-join-delay-ticks", 20));
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (player.isOnline()) {
                claim(player, kit, true);
            }
        }, delay);
    }

    public Set<String> names() {
        ConfigurationSection kits = this.plugin.configs().kits().getConfigurationSection("kits");
        return kits == null ? Set.of() : kits.getKeys(false);
    }

    private ConfigurationSection pageSection(ConfigurationSection root, int page) {
        if (root == null) {
            return null;
        }
        if (page <= 1) {
            return root;
        }
        return root.getConfigurationSection("pages." + page);
    }

    private void give(Player player, ConfigurationSection kit) {
        List<Map<?, ?>> items = kit.getMapList("items");
        for (Map<?, ?> raw : items) {
            giveItem(player, raw);
        }
        for (String command : kit.getStringList("commands")) {
            if (command == null || command.isBlank()) {
                continue;
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", player.getName()));
        }
    }

    private void giveItem(Player player, Map<?, ?> raw) {
        ItemStack stack = previewItem(raw);
        String type = asString(raw.get("mmoitem-type"));
        String id = asString(raw.get("mmoitem-id"));
        int amount = Math.max(1, asInt(raw.get("amount"), 1));
        if (!type.isBlank() && !id.isBlank() && stack == null) {
            MmoItems.give(player, type, id, amount, this.plugin.configs().kits().getString("settings.mmoitems-give", "mi give {player} {type} {id} {amount}"));
            return;
        }
        if (stack != null && !stack.getType().isAir()) {
            player.getInventory().addItem(stack);
        }
    }

    private ItemStack previewItem(Map<?, ?> raw) {
        int amount = Math.max(1, asInt(raw.get("amount"), 1));
        String type = asString(raw.get("mmoitem-type"));
        String id = asString(raw.get("mmoitem-id"));
        if (!type.isBlank() && !id.isBlank()) {
            return MmoItems.stack(type, id, amount);
        }
        Material material = Material.matchMaterial(asString(raw.get("material")));
        if (material == null || material.isAir()) {
            return null;
        }
        return new ItemStack(material, amount);
    }

    private ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.displayName(this.plugin.messages().deserialize(name)));
        return item;
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
