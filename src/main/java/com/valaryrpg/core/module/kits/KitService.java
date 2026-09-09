package com.valaryrpg.core.module.kits;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.gui.GuiLayouts;
import com.valaryrpg.core.gui.MenuHolder;
import com.valaryrpg.core.gui.PanelClicks;
import com.valaryrpg.core.gui.PanelFrames;
import com.valaryrpg.core.gui.PanelItems;
import com.valaryrpg.core.gui.Panels;
import com.valaryrpg.core.gui.SlotSpec;
import com.valaryrpg.core.module.moderation.ModerationModule;
import com.valaryrpg.core.util.ActionCommands;
import com.valaryrpg.core.util.MmoItems;
import com.valaryrpg.core.util.Perms;
import com.valaryrpg.core.util.Sounds;
import com.valaryrpg.core.util.Tasks;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Claim kit da GUI o comando: sinistro ritira, destro anteprima, cooldown SQL.
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
        UUID uuid = player.getUniqueId();
        Tasks.async(this.plugin, () -> {
            Map<String, Long> last = Map.of();
            try {
                last = this.claims.lastClaims(uuid);
            } catch (Exception exception) {
                this.plugin.getLogger().warning("Kit claims SQL fallito per " + player.getName() + ": " + exception.getMessage());
            }
            Map<String, Long> loaded = last;
            Tasks.sync(this.plugin, () -> {
                if (player.isOnline()) {
                    render(player, page, loaded);
                }
            });
        });
    }

    public void click(Player player, int slot, int page, ClickType click) {
        ConfigurationSection layout = pageSection(this.plugin.configs().kits().getConfigurationSection("gui"), page);
        ConfigurationSection button = GuiLayouts.button(layout, slot);
        if (button == null) {
            return;
        }
        String action = GuiLayouts.action(button);
        if (action.equals("kit")) {
            String kitId = button.getString("kit", "");
            if (kitId == null || kitId.isBlank()) {
                return;
            }
            if (rightPreview(click)) {
                Sounds.playRaw(this.plugin, player, button.getString("click-sound", ""), "gui.click-sound");
                preview(player, kitId);
                return;
            }
            if (click.isLeftClick()) {
                PanelClicks.run(this.plugin, player, button);
                claim(player, kitId, false);
            }
            return;
        }
        PanelClicks.run(this.plugin, player, button);
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
        Component title;
        if (titleRaw == null || titleRaw.isBlank()) {
            title = this.plugin.messages().component("kits.preview-title", Map.of("kit", kitId));
        } else {
            title = this.plugin.messages().deserialize(titleRaw.replace("{kit}", kitId).replace("{player}", player.getName()));
        }
        MenuHolder holder = new MenuHolder(PREVIEW, 1, kitId);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, title);
        holder.inventory(inventory);
        PanelFrames.apply(this.plugin, inventory, preview);
        int closeSlot = preview == null ? inventory.getSize() - 1 : preview.getInt("close-slot", inventory.getSize() - 1);
        List<Integer> content = SlotSpec.expand(preview == null ? "" : preview.getString("content-slots", ""));
        int cursor = 0;
        for (Map<?, ?> raw : kit.getMapList("items")) {
            ItemStack stack = itemFrom(raw, player);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            int targetSlot;
            if (content.isEmpty()) {
                targetSlot = cursor;
                cursor++;
            } else {
                if (cursor >= content.size()) {
                    break;
                }
                targetSlot = content.get(cursor);
                cursor++;
            }
            if (targetSlot < 0 || targetSlot >= inventory.getSize() || targetSlot == closeSlot) {
                continue;
            }
            inventory.setItem(targetSlot, stack);
        }
        if (closeSlot >= 0 && closeSlot < inventory.getSize()) {
            ConfigurationSection close = preview == null ? null : preview.getConfigurationSection("close");
            inventory.setItem(closeSlot, close == null
                    ? named(Material.ARROW, this.plugin.messages().text("kits.preview-close-name"))
                    : PanelItems.from(this.plugin, close, player));
        }
        player.openInventory(inventory);
        PanelClicks.openSound(this.plugin, player, preview);
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
            try {
                Optional<Long> last = bypass ? Optional.empty() : this.claims.lastClaim(uuid, id);
                if (!bypass && oneTime && last.isPresent()) {
                    Tasks.sync(this.plugin, () -> this.plugin.messages().send(staff, "kits.one-time", Map.of("kit", id)));
                    return;
                }
                long now = System.currentTimeMillis();
                if (last.isPresent() && cooldownMs > 0 && now - last.get() < cooldownMs) {
                    long left = (cooldownMs - (now - last.get()) + 999) / 1000;
                    Tasks.sync(this.plugin, () -> this.plugin.messages().send(staff, "kits.cooldown", Map.of(
                            "kit", id,
                            "seconds", String.valueOf(left)
                    )));
                    return;
                }
                this.claims.mark(uuid, id, now);
                Tasks.sync(this.plugin, () -> {
                    if (!target.isOnline()) {
                        return;
                    }
                    if (staff == target && this.plugin.configs().kits().getBoolean("settings.close-on-claim", true)) {
                        target.closeInventory();
                    }
                    give(target, kit, id);
                    this.plugin.messages().send(target, "kits.received", Map.of("kit", id));
                    if (staff != target) {
                        this.plugin.messages().send(staff, "kits.given", Map.of("kit", id, "player", target.getName()));
                    }
                });
            } finally {
                Tasks.sync(this.plugin, () -> this.claiming.remove(uuid));
            }
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
            if (player.isOnline() && !held(player)) {
                claim(player, kit, true);
            }
        }, delay);
    }

    public Set<String> names() {
        ConfigurationSection kits = this.plugin.configs().kits().getConfigurationSection("kits");
        return kits == null ? Set.of() : kits.getKeys(false);
    }

    private void render(Player player, int page, Map<String, Long> last) {
        ConfigurationSection root = this.plugin.configs().kits().getConfigurationSection("gui");
        ConfigurationSection layout = pageSection(root, page);
        if (layout == null) {
            this.plugin.messages().send(player, "kits.no-page", Map.of("page", String.valueOf(page)));
            return;
        }
        Inventory inventory = Panels.open(
                this.plugin,
                player,
                new MenuHolder(MENU, page, ""),
                layout,
                root,
                Map.of("page", String.valueOf(page))
        );
        decorateKitIcons(player, inventory, layout, last);
    }

    private void decorateKitIcons(Player player, Inventory inventory, ConfigurationSection layout, Map<String, Long> last) {
        ConfigurationSection slots = layout == null ? null : layout.getConfigurationSection("slots");
        if (slots == null) {
            return;
        }
        for (String key : slots.getKeys(false)) {
            ConfigurationSection button = slots.getConfigurationSection(key);
            if (button == null || !GuiLayouts.action(button).equals("kit")) {
                continue;
            }
            String kitId = button.getString("kit", "");
            if (kitId == null || kitId.isBlank()) {
                continue;
            }
            for (int slot : SlotSpec.expand(key)) {
                ItemStack icon = inventory.getItem(slot);
                if (icon == null || icon.getType().isAir()) {
                    continue;
                }
                inventory.setItem(slot, withStatus(player, icon, kitId.toLowerCase(Locale.ROOT), last));
            }
        }
    }

    private ItemStack withStatus(Player player, ItemStack icon, String kitId, Map<String, Long> last) {
        ItemStack copy = icon.clone();
        List<Component> lore = copy.lore() == null ? new ArrayList<>() : new ArrayList<>(copy.lore());
        lore.add(Component.empty());
        lore.add(this.plugin.messages().component("kits.hint-claim", Map.of()));
        lore.add(this.plugin.messages().component("kits.hint-preview", Map.of()));
        lore.add(statusLine(player, kitId, last));
        copy.editMeta(meta -> meta.lore(lore));
        return copy;
    }

    private Component statusLine(Player player, String kitId, Map<String, Long> last) {
        ConfigurationSection kit = this.plugin.configs().kits().getConfigurationSection("kits." + kitId);
        if (kit == null) {
            return this.plugin.messages().component("kits.status-ready", Map.of());
        }
        String permission = kit.getString("permission", "");
        if (permission != null && !permission.isBlank()
                && !player.hasPermission(permission)
                && !Perms.has(this.plugin, player, "kit-bypass")) {
            return this.plugin.messages().component("kits.status-locked", Map.of());
        }
        Long claimed = last.get(kitId);
        if (kit.getBoolean("one-time", false) && claimed != null && !Perms.has(this.plugin, player, "kit-bypass")) {
            return this.plugin.messages().component("kits.status-one-time-used", Map.of());
        }
        long cooldownMs = kit.getLong("cooldown-seconds", 0L) * 1000L;
        if (claimed != null && cooldownMs > 0 && !Perms.has(this.plugin, player, "kit-bypass")) {
            long left = (cooldownMs - (System.currentTimeMillis() - claimed) + 999) / 1000;
            if (left > 0) {
                return this.plugin.messages().component("kits.status-cooldown", Map.of("seconds", String.valueOf(left)));
            }
        }
        return this.plugin.messages().component("kits.status-ready", Map.of());
    }

    private boolean rightPreview(ClickType click) {
        if (!this.plugin.configs().kits().getBoolean("settings.right-click-preview", true)) {
            return false;
        }
        return click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
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

    private void give(Player player, ConfigurationSection kit, String kitId) {
        boolean drop = this.plugin.configs().kits().getBoolean("settings.drop-overflow", true);
        boolean overflow = false;
        for (Map<?, ?> raw : kit.getMapList("items")) {
            overflow |= giveItem(player, raw, drop);
        }
        ActionCommands.run(this.plugin, player, kit.getStringList("commands"), Map.of("kit", kitId));
        if (overflow) {
            this.plugin.messages().send(player, "kits.inventory-full");
        }
    }

    private boolean giveItem(Player player, Map<?, ?> raw, boolean dropOverflow) {
        String type = asString(raw.get("mmoitem-type"));
        String id = asString(raw.get("mmoitem-id"));
        int amount = Math.max(1, asInt(raw.get("amount"), 1));
        ItemStack stack = itemFrom(raw, player);
        if (!type.isBlank() && !id.isBlank() && (stack == null || stack.getType().isAir())) {
            MmoItems.give(player, type, id, amount, this.plugin.configs().kits().getString("settings.mmoitems-give", "mi give {player} {type} {id} {amount}"));
            return false;
        }
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (leftover.isEmpty()) {
            return false;
        }
        if (dropOverflow && player.getWorld() != null) {
            for (ItemStack extra : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
        }
        return true;
    }

    private ItemStack itemFrom(Map<?, ?> raw, Player viewer) {
        MemoryConfiguration section = new MemoryConfiguration();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                section.set(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        ItemStack stack = PanelItems.from(this.plugin, section, viewer);
        return stack.getType().isAir() ? null : stack;
    }

    private ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.displayName(this.plugin.messages().deserialize(name)));
        return item;
    }

    private boolean held(Player player) {
        ModerationModule moderation = this.plugin.modules().get("moderation");
        return moderation != null && moderation.holds() != null && moderation.holds().holding(player.getUniqueId());
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
