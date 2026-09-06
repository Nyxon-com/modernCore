package com.valaryrpg.core.module.kits;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.gui.GuiLayouts;
import com.valaryrpg.core.gui.PanelClicks;
import com.valaryrpg.core.gui.Panels;
import com.valaryrpg.core.util.MmoItems;
import com.valaryrpg.core.util.Perms;
import com.valaryrpg.core.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Claim kit da GUI o comando, con cooldown SQL e give vanilla/MMOItems.
 */
public final class KitService {

    public static final String MENU = "kits";

    private final ValaryCorePlugin plugin;
    private final KitClaimRepository claims;
    private final Set<UUID> claiming = ConcurrentHashMap.newKeySet();

    public KitService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
        this.claims = new KitClaimRepository(plugin);
    }

    public void open(Player player) {
        Panels.open(this.plugin, player, MENU, this.plugin.configs().kits().getConfigurationSection("gui"), Map.of());
    }

    public void click(Player player, int slot) {
        ConfigurationSection button = GuiLayouts.button(this.plugin.configs().kits().getConfigurationSection("gui"), slot);
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
        }
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
        boolean bypass = ignoreCooldown || Perms.has(this.plugin, target, "kit-bypass") || Perms.has(this.plugin, staff, "kit-bypass");
        Tasks.async(this.plugin, () -> {
            Optional<Long> last = bypass ? Optional.empty() : this.claims.lastClaim(uuid, id);
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
        int amount = Math.max(1, asInt(raw.get("amount"), 1));
        String type = asString(raw.get("mmoitem-type"));
        String id = asString(raw.get("mmoitem-id"));
        if (!type.isBlank() && !id.isBlank()) {
            MmoItems.give(player, type, id, amount, this.plugin.configs().kits().getString("settings.mmoitems-give", "mi give {player} {type} {id} {amount}"));
            return;
        }
        Material material = Material.matchMaterial(asString(raw.get("material")));
        if (material == null || material.isAir()) {
            return;
        }
        player.getInventory().addItem(new ItemStack(material, amount));
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
