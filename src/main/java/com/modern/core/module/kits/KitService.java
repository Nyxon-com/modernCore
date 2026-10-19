package com.modern.core.module.kits;

import com.modern.core.ModernCorePlugin;
import com.modern.core.gui.MenuHolder;
import com.modern.core.gui.PanelItems;
import com.modern.core.module.moderation.ModerationModule;
import com.modern.core.util.ActionCommands;
import com.modern.core.util.Durations;
import com.modern.core.util.MmoItems;
import com.modern.core.util.Perms;
import com.modern.core.util.Sounds;
import com.modern.core.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Claim kit da GUI o comando: sinistro ritira, destro anteprima, cooldown in cache.
 */
public final class KitService {

    public static final String MENU = "kits";
    public static final String PREVIEW = "kit-preview";

    final ModernCorePlugin plugin;
    final KitClaimRepository claims;
    private final KitMenus menus;
    private final Set<UUID> claiming = ConcurrentHashMap.newKeySet();

    public KitService(ModernCorePlugin plugin) {
        this.plugin = plugin;
        this.claims = new KitClaimRepository(plugin);
        this.menus = new KitMenus(this);
    }

    public void open(Player player) {
        this.menus.open(player);
    }

    public void open(Player player, int page) {
        this.menus.open(player, page);
    }

    public void prefetch(Player player) {
        UUID uuid = player.getUniqueId();
        Tasks.async(this.plugin, () -> {
            try {
                this.claims.lastClaims(uuid);
            } catch (Exception exception) {
                this.plugin.getLogger().warning("Kit claims SQL fallito per " + player.getName() + ": " + exception.getMessage());
            }
        });
    }

    public void forget(Player player) {
        this.claims.forget(player.getUniqueId());
        this.claiming.remove(player.getUniqueId());
    }

    public void shutdown() {
        this.claims.clear();
        this.claiming.clear();
    }

    public void click(Player player, int slot, int page, ClickType click) {
        this.menus.click(player, slot, page, click);
    }

    public void previewClick(Player player, int slot, MenuHolder holder) {
        this.menus.previewClick(player, slot, holder);
    }

    public void preview(Player player, String kitId) {
        this.menus.preview(player, kitId);
    }

    public void preview(Player player, String kitId, int fromPage) {
        this.menus.preview(player, kitId, fromPage);
    }

    public boolean closePreviewSlot(int slot) {
        return this.menus.closePreviewSlot(slot);
    }

    public boolean claimPreviewSlot(int slot) {
        return this.menus.claimPreviewSlot(slot);
    }

    public void claim(CommandSender staff, Player target, String kitId, boolean ignoreCooldown) {
        ConfigurationSection kit = this.plugin.configs().kits().getConfigurationSection("kits." + kitId.toLowerCase(Locale.ROOT));
        if (kit == null) {
            fail(staff, "kits.unknown", Map.of("kit", kitId));
            return;
        }
        boolean gifted = staff != target;
        String permission = kit.getString("permission", "");
        if (permission != null && !permission.isBlank() && !target.hasPermission(permission)
                && !Perms.has(this.plugin, staff, "kit-bypass") && !gifted) {
            fail(staff, "kits.no-permission", Map.of("kit", kitId));
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
        boolean bypass = ignoreCooldown || gifted
                || Perms.has(this.plugin, target, "kit-bypass")
                || Perms.has(this.plugin, staff, "kit-bypass");
        Tasks.async(this.plugin, () -> {
            try {
                Optional<Long> last = bypass ? Optional.empty() : this.claims.lastClaim(uuid, id);
                if (!bypass && oneTime && last.isPresent()) {
                    Tasks.sync(this.plugin, () -> fail(staff, "kits.one-time", Map.of("kit", id)));
                    return;
                }
                long now = System.currentTimeMillis();
                if (last.isPresent() && cooldownMs > 0 && now - last.get() < cooldownMs) {
                    long readyAt = last.get() + cooldownMs;
                    Tasks.sync(this.plugin, () -> fail(staff, "kits.cooldown", Map.of(
                            "kit", id,
                            "duration", Durations.formatRemaining(this.plugin, readyAt),
                            "seconds", String.valueOf((cooldownMs - (now - last.get()) + 999) / 1000)
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
                    Sounds.play(this.plugin, target, "kits-claim");
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

    ItemStack itemFrom(Map<?, ?> raw, Player viewer) {
        MemoryConfiguration section = new MemoryConfiguration();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                section.set(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        ItemStack stack = PanelItems.from(this.plugin, section, viewer);
        return stack.getType().isAir() ? null : stack;
    }

    void fail(CommandSender staff, String key, Map<String, String> values) {
        this.plugin.messages().send(staff, key, values);
        if (staff instanceof Player player) {
            Sounds.play(this.plugin, player, "kits-deny");
        }
    }

    private boolean held(Player player) {
        ModerationModule moderation = this.plugin.modules().get("moderation");
        return moderation != null && moderation.holds() != null && moderation.holds().holding(player.getUniqueId());
    }

    static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    static int asInt(Object value, int fallback) {
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
