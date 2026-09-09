package com.valaryrpg.core.module.mechanics;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.util.ActionCommands;
import com.valaryrpg.core.util.MmoItems;
import com.valaryrpg.core.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Portali cuboidi: ingresso su cambio blocco, azioni al tick successivo (non in move).
 */
public final class PortalService {

    private final ValaryCorePlugin plugin;
    private final PortalSelection selection;
    private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final List<Region> regions = new CopyOnWriteArrayList<>();

    public PortalService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
        this.selection = new PortalSelection(plugin);
        rebuild();
    }

    public void rebuild() {
        List<Region> next = new ArrayList<>();
        ConfigurationSection portals = this.plugin.configs().mechanics().getConfigurationSection("portals");
        if (portals != null) {
            for (String name : portals.getKeys(false)) {
                ConfigurationSection portal = portals.getConfigurationSection(name);
                Region region = regionOf(name, portal);
                if (region != null) {
                    next.add(region);
                }
            }
        }
        this.regions.clear();
        this.regions.addAll(next);
    }

    public void tryEnter(Player player, Location from, Location to) {
        if (player == null || to == null || to.getWorld() == null) {
            return;
        }
        String entered = nameAt(to);
        if (entered == null || entered.equals(nameAt(from))) {
            return;
        }
        activateLater(player, entered);
    }

    public void unload(UUID uuid) {
        this.selection.forget(uuid);
        this.cooldownUntil.remove(uuid);
        this.pending.remove(uuid);
    }

    public void markPos1(Player player) {
        this.selection.markPos1(player);
    }

    public void markPos1(Player player, Location location) {
        this.selection.markPos1(player, location);
    }

    public void markPos2(Player player) {
        this.selection.markPos2(player);
    }

    public void markPos2(Player player, Location location) {
        this.selection.markPos2(player, location);
    }

    public void markDest(Player player) {
        this.selection.markDest(player);
    }

    public void create(Player player, String name) {
        String id = normalize(name);
        if (id.isBlank()) {
            this.plugin.messages().send(player, "portals.invalid-name");
            return;
        }
        Location first = this.selection.pos1(player.getUniqueId());
        Location second = this.selection.pos2(player.getUniqueId());
        Location destination = this.selection.dest(player.getUniqueId());
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null) {
            this.plugin.messages().send(player, "portals.incomplete");
            return;
        }
        if (!first.getWorld().equals(second.getWorld())) {
            this.plugin.messages().send(player, "portals.world-mismatch");
            return;
        }
        FileConfiguration yaml = this.plugin.configs().mechanics();
        String base = "portals." + id;
        yaml.set(base, null);
        yaml.set(base + ".enabled", true);
        yaml.set(base + ".world", first.getWorld().getName());
        writeBlock(yaml, base + ".pos1", first);
        writeBlock(yaml, base + ".pos2", second);
        if (destination != null && destination.getWorld() != null) {
            writeDestination(yaml, base + ".destination", destination);
        }
        yaml.set(base + ".commands", List.of());
        yaml.set(base + ".permission", "");
        yaml.set(base + ".warmup-seconds", 0);
        this.plugin.configs().saveMechanics();
        rebuild();
        this.plugin.messages().send(player, "portals.created", Map.of("portal", id));
    }

    public void setItem(Player player, String name) {
        String id = requirePortal(player, name);
        if (id == null) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        FileConfiguration yaml = this.plugin.configs().mechanics();
        String base = "portals." + id + ".item";
        if (hand == null || hand.getType().isAir()) {
            yaml.set("portals." + id + ".item", null);
            this.plugin.configs().saveMechanics();
            this.plugin.messages().send(player, "portals.item-cleared", Map.of("portal", id));
            return;
        }
        yaml.set(base + ".material", hand.getType().name());
        yaml.set(base + ".amount", 1);
        yaml.set(base + ".consume", true);
        String[] nbt = MmoItems.typeAndId(hand);
        if (nbt != null && isMmoId(nbt[0]) && isMmoId(nbt[1])) {
            yaml.set(base + ".mmoitem-type", nbt[0]);
            yaml.set(base + ".mmoitem-id", nbt[1]);
        } else {
            yaml.set(base + ".mmoitem-type", "");
            yaml.set(base + ".mmoitem-id", "");
        }
        this.plugin.configs().saveMechanics();
        this.plugin.messages().send(player, "portals.item-set", Map.of("portal", id));
    }

    public void enable(CommandSender sender, String name, boolean enabled) {
        String id = requirePortal(sender, name);
        if (id == null) {
            return;
        }
        this.plugin.configs().mechanics().set("portals." + id + ".enabled", enabled);
        this.plugin.configs().saveMechanics();
        rebuild();
        this.plugin.messages().send(sender, enabled ? "portals.enabled" : "portals.disabled", Map.of("portal", id));
    }

    public void delete(CommandSender sender, String name) {
        String id = requirePortal(sender, name);
        if (id == null) {
            return;
        }
        this.plugin.configs().mechanics().set("portals." + id, null);
        this.plugin.configs().saveMechanics();
        rebuild();
        this.plugin.messages().send(sender, "portals.deleted", Map.of("portal", id));
    }

    public void list(CommandSender sender) {
        ConfigurationSection portals = this.plugin.configs().mechanics().getConfigurationSection("portals");
        if (portals == null || portals.getKeys(false).isEmpty()) {
            this.plugin.messages().send(sender, "portals.list-empty");
            return;
        }
        List<String> lines = new ArrayList<>();
        for (String name : portals.getKeys(false)) {
            ConfigurationSection portal = portals.getConfigurationSection(name);
            boolean on = portal == null || portal.getBoolean("enabled", true);
            lines.add(name + (on ? "" : " (off)"));
        }
        this.plugin.messages().send(sender, "portals.list", Map.of(
                "count", String.valueOf(lines.size()),
                "portals", String.join(", ", lines)
        ));
    }

    public Set<String> names() {
        ConfigurationSection portals = this.plugin.configs().mechanics().getConfigurationSection("portals");
        return portals == null ? Set.of() : portals.getKeys(false);
    }

    public void giveWand(Player player) {
        this.selection.giveWand(player);
    }

    public boolean isWand(ItemStack item) {
        return this.selection.isWand(item);
    }

    public void handleWand(Player player, Block block, boolean sneak, boolean leftClick) {
        this.selection.handleWand(player, block, sneak, leftClick);
    }

    public void setCommand(CommandSender sender, String name, String command) {
        String id = requirePortal(sender, name);
        if (id == null) {
            return;
        }
        String value = command == null ? "" : command.trim();
        if (value.isBlank()) {
            this.plugin.messages().send(sender, "portals.command-usage", Map.of("label", "portal"));
            return;
        }
        List<String> commands = new ArrayList<>(this.plugin.configs().mechanics().getStringList("portals." + id + ".commands"));
        commands.add(value);
        this.plugin.configs().mechanics().set("portals." + id + ".commands", commands);
        this.plugin.configs().saveMechanics();
        this.plugin.messages().send(sender, "portals.command-set", Map.of(
                "portal", id,
                "command", value
        ));
    }

    public void clearCommands(CommandSender sender, String name) {
        String id = requirePortal(sender, name);
        if (id == null) {
            return;
        }
        this.plugin.configs().mechanics().set("portals." + id + ".commands", List.of());
        this.plugin.configs().saveMechanics();
        this.plugin.messages().send(sender, "portals.commands-cleared", Map.of("portal", id));
    }

    private void activateLater(Player player, String portalId) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long until = this.cooldownUntil.get(uuid);
        if (until != null && until > now) {
            return;
        }
        if (!this.pending.add(uuid)) {
            return;
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            this.pending.remove(uuid);
            if (player.isOnline()) {
                activate(player, portalId);
            }
        });
    }

    private void activate(Player player, String entered) {
        ConfigurationSection portal = this.plugin.configs().mechanics().getConfigurationSection("portals." + entered);
        if (portal == null || !portal.getBoolean("enabled", true)) {
            return;
        }
        String permission = portal.getString("permission", "");
        if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
            this.plugin.messages().send(player, "portals.no-permission", Map.of("portal", entered));
            return;
        }
        ConfigurationSection item = portal.getConfigurationSection("item");
        if (item != null && !consumeOrMatch(player, item)) {
            this.plugin.messages().send(player, "portals.need-item", Map.of("portal", entered));
            return;
        }
        this.plugin.lastLocations().remember(player);
        List<String> commands = portal.getStringList("commands");
        Location destination = destinationOf(portal);
        if (destination == null && commands.isEmpty()) {
            this.plugin.messages().send(player, "portals.invalid-destination", Map.of("portal", entered));
            return;
        }
        long cooldown = Math.max(0L, this.plugin.configs().mechanics().getLong("portal-settings.enter-cooldown-millis", 1500L));
        this.cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + cooldown);
        if (!commands.isEmpty()) {
            ActionCommands.run(this.plugin, player, commands, Map.of("portal", entered));
        }
        Sounds.play(this.plugin, player, "portals-enter");
        if (destination == null) {
            this.plugin.messages().send(player, "portals.used", Map.of("portal", entered));
            return;
        }
        int warmup = Math.max(0, portal.getInt("warmup-seconds", 0));
        if (warmup > 0) {
            this.plugin.messages().send(player, "portals.warmup", Map.of(
                    "portal", entered,
                    "seconds", String.valueOf(warmup)
            ));
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> teleport(player, destination), warmup * 20L);
        } else {
            teleport(player, destination);
        }
        this.plugin.messages().send(player, "portals.used", Map.of("portal", entered));
    }

    private void teleport(Player player, Location destination) {
        if (!player.isOnline() || destination.getWorld() == null) {
            return;
        }
        player.teleport(destination);
        this.plugin.teleports().safeLand(player);
    }

    private String requirePortal(CommandSender sender, String name) {
        String id = normalize(name);
        if (this.plugin.configs().mechanics().getConfigurationSection("portals." + id) == null) {
            this.plugin.messages().send(sender, "portals.unknown", Map.of("portal", name));
            return null;
        }
        return id;
    }

    private static void writeBlock(FileConfiguration yaml, String path, Location location) {
        yaml.set(path + ".x", location.getBlockX());
        yaml.set(path + ".y", location.getBlockY());
        yaml.set(path + ".z", location.getBlockZ());
    }

    private static void writeDestination(FileConfiguration yaml, String path, Location location) {
        if (location.getWorld() != null) {
            yaml.set(path + ".world", location.getWorld().getName());
        }
        yaml.set(path + ".x", location.getX());
        yaml.set(path + ".y", location.getY());
        yaml.set(path + ".z", location.getZ());
        yaml.set(path + ".yaw", location.getYaw());
        yaml.set(path + ".pitch", location.getPitch());
    }

    private static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    private static boolean isMmoId(String value) {
        return value != null && !value.isBlank() && !"null".equalsIgnoreCase(value);
    }

    private String nameAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        int padding = Math.max(0, this.plugin.configs().mechanics().getInt("portal-settings.y-padding", 1));
        String world = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        for (Region region : this.regions) {
            if (region.contains(world, x, y, z, padding)) {
                return region.id;
            }
        }
        return null;
    }

    private Region regionOf(String name, ConfigurationSection portal) {
        if (portal == null || !portal.getBoolean("enabled", true)) {
            return null;
        }
        ConfigurationSection pos1 = portal.getConfigurationSection("pos1");
        ConfigurationSection pos2 = portal.getConfigurationSection("pos2");
        if (pos1 == null || pos2 == null) {
            return null;
        }
        String world = portal.getString("world", "");
        if (world == null || world.isBlank()) {
            return null;
        }
        int x1 = pos1.getInt("x", (int) Math.floor(pos1.getDouble("x")));
        int y1 = pos1.getInt("y", (int) Math.floor(pos1.getDouble("y")));
        int z1 = pos1.getInt("z", (int) Math.floor(pos1.getDouble("z")));
        int x2 = pos2.getInt("x", (int) Math.floor(pos2.getDouble("x")));
        int y2 = pos2.getInt("y", (int) Math.floor(pos2.getDouble("y")));
        int z2 = pos2.getInt("z", (int) Math.floor(pos2.getDouble("z")));
        return new Region(
                name,
                world,
                Math.min(x1, x2),
                Math.max(x1, x2),
                Math.min(y1, y2),
                Math.max(y1, y2),
                Math.min(z1, z2),
                Math.max(z1, z2)
        );
    }

    private Location destinationOf(ConfigurationSection portal) {
        ConfigurationSection dest = portal.getConfigurationSection("destination");
        if (dest == null) {
            return null;
        }
        World world = Bukkit.getWorld(dest.getString("world", ""));
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                dest.getDouble("x"),
                dest.getDouble("y"),
                dest.getDouble("z"),
                (float) dest.getDouble("yaw"),
                (float) dest.getDouble("pitch")
        );
    }

    private boolean consumeOrMatch(Player player, ConfigurationSection item) {
        Material material = Material.matchMaterial(item.getString("material", "AIR"));
        String type = item.getString("mmoitem-type", "");
        String id = item.getString("mmoitem-id", "");
        int amount = Math.max(1, item.getInt("amount", 1));
        if ((material == null || material.isAir()) && (type == null || type.isBlank()) && (id == null || id.isBlank())) {
            return true;
        }
        PlayerInventory inventory = player.getInventory();
        int slot = findSlot(inventory, material, type, id, amount);
        if (slot < 0) {
            return false;
        }
        if (!item.getBoolean("consume", true)) {
            return true;
        }
        ItemStack stack = inventory.getItem(slot);
        if (stack == null) {
            return false;
        }
        int left = stack.getAmount() - amount;
        if (left <= 0) {
            inventory.setItem(slot, null);
        } else {
            stack.setAmount(left);
        }
        return true;
    }

    private int findSlot(PlayerInventory inventory, Material material, String type, String id, int amount) {
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir() || stack.getAmount() < amount) {
                continue;
            }
            if (material != null && material != Material.AIR && stack.getType() != material) {
                continue;
            }
            if (!MmoItems.matches(stack, type, id)) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private record Region(String id, String world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        private boolean contains(String worldName, int x, int y, int z, int yPadding) {
            return this.world.equalsIgnoreCase(worldName)
                    && x >= this.minX && x <= this.maxX
                    && z >= this.minZ && z <= this.maxZ
                    && y >= this.minY && y <= this.maxY + yPadding;
        }
    }
}
